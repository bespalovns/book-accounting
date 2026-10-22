const definitions = {
    authors: {
        title: "Авторы",
        addTitle: "Добавить автора",
        editTitle: "Редактировать автора",
        fields: [
            {
                name: "last_name",
                label: "Фамилия",
                type: "text",
                required: true,
                max: 100,
            },
            {
                name: "first_name",
                label: "Имя",
                type: "text",
                required: true,
                max: 100,
            },
        ],
        columns: [
            ["id", "ID"],
            ["last_name", "Фамилия"],
            ["first_name", "Имя"],
        ],
    },

    publishers: {
        title: "Издательства",
        addTitle: "Добавить издательство",
        editTitle: "Редактировать издательство",
        fields: [
            {
                name: "name_publisher",
                label: "Название",
                type: "text",
                required: true,
                max: 200,
            },
            {
                name: "address",
                label: "Адрес",
                type: "text",
                required: true,
                max: 300,
            },
            {
                name: "site",
                label: "Сайт",
                type: "url",
                max: 300,
            },
        ],
        columns: [
            ["id", "ID"],
            ["name_publisher", "Название"],
            ["address", "Адрес"],
            ["site", "Сайт"],
        ],
    },

    books: {
        title: "Книги",
        addTitle: "Добавить книгу",
        editTitle: "Редактировать книгу",
        fields: [
            {
                name: "title",
                label: "Название",
                type: "text",
                required: true,
                max: 250,
            },
            {
                name: "author_id",
                label: "Автор",
                type: "select",
                required: true,
            },
            {
                name: "publisher_id",
                label: "Издательство",
                type: "select",
                required: true,
            },
            {
                name: "code",
                label: "ISBN",
                type: "text",
                required: true,
                max: 40,
            },
            {
                name: "year_publish",
                label: "Год издания",
                type: "number",
                required: true,
                min: 1,
                max: 9999,
                step: 1,
            },
            {
                name: "count_page",
                label: "Количество страниц",
                type: "number",
                required: true,
                min: 1,
                max: 2147483647,
                step: 1,
            },
            {
                name: "price",
                label: "Цена, руб.",
                type: "number",
                required: true,
                min: 0,
                max: 99999999.99,
                step: 0.01,
            },
            {
                name: "hardcover",
                label: "Твердый переплет",
                type: "checkbox",
            },
            {
                name: "status",
                label: "В наличии",
                type: "checkbox",
            },
            {
                name: "abstract",
                label: "Аннотация",
                type: "textarea",
                max: 10000,
            },
        ],
        columns: [
            ["id", "ID"],
            ["title", "Название"],
            ["author_name", "Автор"],
            ["publisher_name", "Издательство"],
            ["code", "ISBN"],
            ["year_publish", "Год"],
            ["count_page", "Страницы"],
            ["price", "Цена, руб."],
            ["hardcover", "Переплет"],
            ["status", "Наличие"],
        ],
    },
};


const form = document.querySelector("#record-form");
const fieldsBox = document.querySelector("#form-fields");
const formTitle = document.querySelector("#form-title");
const formHint = document.querySelector("#form-hint");

const saveButton = document.querySelector("#save-button");
const cancelButton = document.querySelector("#cancel-button");
const refreshButton = document.querySelector("#refresh-button");

const listTitle = document.querySelector("#list-title");
const listState = document.querySelector("#list-state");
const tableHead = document.querySelector("#table-head");
const tableBody = document.querySelector("#table-body");

const messageBox = document.querySelector("#message");

const searchForm = document.querySelector("#search-form");
const searchInput = document.querySelector("#search-input");
const statusFilter = document.querySelector("#status-filter");
const resetSearchButton =
    document.querySelector("#reset-search-button");

const tabs = [...document.querySelectorAll("[data-entity]")];

let currentEntity = "books";
let editingId = null;
let busy = false;

// Записи, полученные от сервера для текущего списка.
let currentRecords = [];

// Отдельные настройки сортировки для каждого раздела.
const sortSettings = {
    books: { key: "id", direction: "asc" },
    authors: { key: "id", direction: "asc" },
    publishers: { key: "id", direction: "asc" },
};

// Сравнение текста с учетом русского языка и чисел в строках.
const textCollator = new Intl.Collator("ru", {
    sensitivity: "base",
    numeric: true,
});

const numericColumns = new Set([
    "id",
    "year_publish",
    "count_page",
    "price",
]);

const booleanColumns = new Set([
    "hardcover",
    "status",
]);

let references = {
    authors: [],
    publishers: [],
};


function showMessage(text, type = "success") {
    messageBox.textContent = text;
    messageBox.className = `message ${type}`;
    messageBox.hidden = false;
}


function hideMessage() {
    messageBox.hidden = true;
    messageBox.textContent = "";
}


function referencesMissing() {
    return currentEntity === "books" && (
        references.authors.length === 0 ||
        references.publishers.length === 0
    );
}


function updateButtons() {
    document.querySelectorAll("button").forEach((button) => {
        button.disabled = busy;
    });

    if (!busy && referencesMissing()) {
        saveButton.disabled = true;
    }
}


async function runAction(action) {
    if (busy) {
        return;
    }

    busy = true;
    updateButtons();

    try {
        await action();
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        busy = false;
        updateButtons();
    }
}


async function apiRequest(url, options = {}) {
    let response;

    try {
        response = await fetch(url, {
            ...options,
            headers: {
                ...(options.body
                    ? { "Content-Type": "application/json" }
                    : {}),
                ...(options.headers || {}),
            },
        });
    } catch {
        throw new Error(
            "Не удалось связаться с сервером. " +
            "Проверьте, запущен ли Flask."
        );
    }

    if (response.status === 204) {
        return null;
    }

    const data = await response.json().catch(() => null);

    if (!response.ok) {
        throw new Error(
            data?.message || `Ошибка запроса: ${response.status}`
        );
    }

    if (data === null) {
        throw new Error("Сервер вернул некорректный ответ.");
    }

    return data;
}


async function loadReferences() {
    const [authors, publishers] = await Promise.all([
        apiRequest("/api/authors"),
        apiRequest("/api/publishers"),
    ]);

    references = { authors, publishers };
}


function fillSelect(select, fieldName) {
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Выберите...";
    select.appendChild(placeholder);

    const items = fieldName === "author_id"
        ? references.authors
        : references.publishers;

    for (const item of items) {
        const option = document.createElement("option");
        option.value = item.id;

        option.textContent = fieldName === "author_id"
            ? `${item.last_name} ${item.first_name} (ID ${item.id})`
            : `${item.name_publisher} (ID ${item.id})`;

        select.appendChild(option);
    }
}


function renderForm(record = null) {
    const definition = definitions[currentEntity];

    fieldsBox.replaceChildren();

    formTitle.textContent = record
        ? definition.editTitle
        : definition.addTitle;

    saveButton.textContent = record
        ? "Сохранить изменения"
        : "Добавить";

    cancelButton.hidden = !record;

    formHint.textContent = referencesMissing()
        ? "Сначала добавьте хотя бы одного автора и издательство."
        : "Поля со звездочкой обязательны.";

    for (const field of definition.fields) {
        const wrapper = document.createElement("div");
        wrapper.className = "field";

        if (field.type === "textarea") {
            wrapper.classList.add("full");
        }

        const label = document.createElement("label");
        const id = `field-${field.name}`;

        label.htmlFor = id;
        label.textContent =
            field.label + (field.required ? " *" : "");

        let input;

        if (field.type === "textarea") {
            input = document.createElement("textarea");
        } else if (field.type === "select") {
            input = document.createElement("select");
            fillSelect(input, field.name);
        } else {
            input = document.createElement("input");
            input.type = field.type;
        }

        input.id = id;
        input.name = field.name;
        input.required = Boolean(field.required);

        if (field.type === "number") {
            input.min = field.min;
            input.max = field.max;
            input.step = field.step;
        } else if (
            field.max &&
            field.type !== "select" &&
            field.type !== "checkbox"
        ) {
            input.maxLength = field.max;
        }

        if (field.type === "checkbox") {
            input.checked = record
                ? Boolean(record[field.name])
                : field.name === "status";

            label.className = "checkbox-label";
            label.prepend(input);
            wrapper.appendChild(label);
        } else {
            input.value = record?.[field.name] ?? "";
            wrapper.append(label, input);
        }

        fieldsBox.appendChild(wrapper);
    }

    updateButtons();
}


function resetEditor() {
    editingId = null;
    renderForm();
}


function collectPayload() {
    const data = {};

    for (const field of definitions[currentEntity].fields) {
        const input = form.elements.namedItem(field.name);

        if (field.type === "checkbox") {
            data[field.name] = input.checked;
        } else if (
            field.type === "select" ||
            (field.type === "number" && field.name !== "price")
        ) {
            data[field.name] = Number(input.value);
        } else {
            data[field.name] = input.value.trim();
        }
    }

    return data;
}


function displayValue(key, value) {
    if (key === "price") {
        return Number(value).toLocaleString("ru-RU", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    }

    if (key === "hardcover") {
        return value ? "Твердый" : "Мягкий";
    }

    return value ?? "—";
}


function recordName(record) {
    if (currentEntity === "books") {
        return record.title;
    }

    if (currentEntity === "publishers") {
        return record.name_publisher;
    }

    return `${record.last_name} ${record.first_name}`;
}

function compareValues(left, right, key) {
    if (numericColumns.has(key)) {
        return Number(left) - Number(right);
    }

    if (booleanColumns.has(key)) {
        return Number(Boolean(left)) - Number(Boolean(right));
    }

    return textCollator.compare(
        String(left ?? ""),
        String(right ?? "")
    );
}


function getSortedRecords(records) {
    const { key, direction } = sortSettings[currentEntity];
    const multiplier = direction === "asc" ? 1 : -1;

    // Создаем копию, чтобы не менять исходный массив.
    return [...records].sort((left, right) => {
        const result = compareValues(
            left[key],
            right[key],
            key
        );

        // Если значения совпадают, сравниваем ID.
        // Это делает порядок одинаковых значений предсказуемым.
        const comparison =
            result || Number(left.id) - Number(right.id);

        return comparison * multiplier;
    });
}


function toggleSort(key) {
    if (busy) {
        return;
    }

    const settings = sortSettings[currentEntity];

    if (settings.key === key) {
        settings.direction =
            settings.direction === "asc" ? "desc" : "asc";
    } else {
        settings.key = key;
        settings.direction = "asc";
    }

    renderTable(currentRecords);

    // Возвращаем фокус на заголовок после перерисовки.
    const button = [...tableHead.querySelectorAll(".sort-button")]
        .find((item) => item.dataset.sortKey === key);

    button?.focus();
}

function renderTable(records) {
    const definition = definitions[currentEntity];

    currentRecords = records;

    const sortedRecords = getSortedRecords(records);
    const settings = sortSettings[currentEntity];

    tableHead.replaceChildren();
    tableBody.replaceChildren();

    const headerRow = document.createElement("tr");

    for (const [key, title] of definition.columns) {
        const cell = document.createElement("th");
        cell.scope = "col";

        const isActive = settings.key === key;

        if (isActive) {
            cell.setAttribute(
                "aria-sort",
                settings.direction === "asc"
                    ? "ascending"
                    : "descending"
            );
        }

        const button = document.createElement("button");
        button.type = "button";
        button.className = "sort-button";
        button.dataset.sortKey = key;

        let arrow = "↕";

        if (isActive) {
            arrow = settings.direction === "asc" ? "▲" : "▼";
        }

        button.textContent = `${title} ${arrow}`;

        const nextDirection =
            isActive && settings.direction === "asc"
                ? "по убыванию"
                : "по возрастанию";

        button.title = `Сортировать ${nextDirection}`;
        button.setAttribute(
            "aria-label",
            `${title}: сортировать ${nextDirection}`
        );

        button.addEventListener("click", () => {
            toggleSort(key);
        });

        cell.appendChild(button);
        headerRow.appendChild(cell);
    }

    const actionsHeader = document.createElement("th");
    actionsHeader.textContent = "Действия";
    headerRow.appendChild(actionsHeader);

    tableHead.appendChild(headerRow);

    for (const record of sortedRecords) {
        const row = document.createElement("tr");

        for (const [key] of definition.columns) {
            const cell = document.createElement("td");

            if (key === "status") {
                const badge = document.createElement("span");
                badge.className = record.status
                    ? "badge available"
                    : "badge";

                badge.textContent = record.status
                    ? "В наличии"
                    : "Нет в наличии";

                cell.appendChild(badge);
            } else {
                cell.textContent = displayValue(key, record[key]);
            }

            row.appendChild(cell);
        }

        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "row-actions";

        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "secondary";
        editButton.textContent = "Изменить";

        editButton.addEventListener("click", () => {
            runAction(async () => {
                hideMessage();

                const recordData = await apiRequest(
                    `/api/${currentEntity}/${record.id}`
                );

                if (currentEntity === "books") {
                    await loadReferences();
                }

                editingId = record.id;
                renderForm(recordData);

                formTitle.scrollIntoView({
                    behavior: "smooth",
                    block: "start",
                });
            });
        });

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "danger";
        deleteButton.textContent = "Удалить";

        deleteButton.addEventListener("click", () => {
            if (busy) {
                return;
            }

            const confirmed = confirm(
                `Удалить запись «${recordName(record)}»?`
            );

            if (!confirmed) {
                return;
            }

            runAction(async () => {
                hideMessage();

                await apiRequest(
                    `/api/${currentEntity}/${record.id}`,
                    { method: "DELETE" }
                );

                if (editingId === record.id) {
                    resetEditor();
                }

                showMessage("Запись удалена.");

                try {
                    await loadList();
                } catch {
                    showMessage(
                        "Запись удалена, но список не обновился. " +
                        "Нажмите «Обновить».",
                        "error"
                    );
                }
            });
        });

        actions.append(editButton, deleteButton);
        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);
        tableBody.appendChild(row);
    }

    listState.textContent = records.length
        ? `Записей в списке: ${records.length}`
        : "Записи не найдены.";

    updateButtons();
}


async function loadList() {
    listState.textContent = "Загрузка...";

    const params = new URLSearchParams();

    if (currentEntity === "books") {
        const search = searchInput.value.trim();
        const status = statusFilter.value;

        if (search) {
            params.set("search", search);
        }

        if (status) {
            params.set("status", status);
        }
    }

    const query = params.toString();
    const url = `/api/${currentEntity}` +
        (query ? `?${query}` : "");

    try {
        const records = await apiRequest(url);
        renderTable(records);
    } catch (error) {
        listState.textContent =
            "Список не обновлен. Проверьте подключение.";
        throw error;
    }
}


async function switchSection(entity) {
    hideMessage();

    currentEntity = entity;
    editingId = null;
    currentRecords = [];

    for (const tab of tabs) {
        const active = tab.dataset.entity === entity;
        tab.classList.toggle("active", active);
        tab.setAttribute("aria-pressed", String(active));
    }

    listTitle.textContent = definitions[entity].title;
    searchForm.hidden = entity !== "books";

    tableHead.replaceChildren();
    tableBody.replaceChildren();
    listState.textContent = "";

    // При неудачной загрузке справочников сохраняем возможность
    // перейти в раздел авторов или издательств.
    if (entity === "books") {
        references = { authors: [], publishers: [] };
    }

    renderForm();

    if (entity === "books") {
        await loadReferences();
        renderForm();
    }

    await loadList();
}


form.addEventListener("submit", (event) => {
    event.preventDefault();

    runAction(async () => {
        hideMessage();

        const payload = collectPayload();
        const isEditing = editingId !== null;

        const url = isEditing
            ? `/api/${currentEntity}/${editingId}`
            : `/api/${currentEntity}`;

        await apiRequest(url, {
            method: isEditing ? "PUT" : "POST",
            body: JSON.stringify(payload),
        });

        resetEditor();

        showMessage(
            isEditing ? "Изменения сохранены." : "Запись добавлена."
        );

        try {
            await loadList();
        } catch {
            showMessage(
                "Данные сохранены, но список не обновился. " +
                "Нажмите «Обновить».",
                "error"
            );
        }
    });
});


cancelButton.addEventListener("click", () => {
    if (busy) {
        return;
    }

    hideMessage();
    resetEditor();
});


refreshButton.addEventListener("click", () => {
    runAction(async () => {
        hideMessage();

        if (currentEntity === "books") {
            const draft = collectPayload();

            await loadReferences();

            for (const fieldName of ["author_id", "publisher_id"]) {
                const select = form.elements.namedItem(fieldName);
                select.replaceChildren();
                fillSelect(select, fieldName);
                select.value = String(draft[fieldName] || "");
            }

            formHint.textContent = referencesMissing()
                ? "Сначала добавьте автора и издательство."
                : "Поля со звездочкой обязательны.";
        }

        await loadList();
    });
});


searchForm.addEventListener("submit", (event) => {
    event.preventDefault();

    runAction(async () => {
        hideMessage();
        await loadList();
    });
});


resetSearchButton.addEventListener("click", () => {
    runAction(async () => {
        hideMessage();
        searchForm.reset();
        await loadList();
    });
});


for (const tab of tabs) {
    tab.addEventListener("click", () => {
        if (busy || tab.dataset.entity === currentEntity) {
            return;
        }

        runAction(() => switchSection(tab.dataset.entity));
    });
}


runAction(() => switchSection("books"));