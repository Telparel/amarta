package com.amarthaloe.rpg;

public enum Faction {
    LOWER_CITY(
        "lower_city",
        "Нижний Город",
        "Трущобы у фундамента хрущёвок, чёрный рынок пайков и братство дворов.",
        0xFFD97724, // Ржаво-янтарный цвет шкалы
        "§6",       // Золотисто-рыжий в чате
        new String[]{"Клеймёный Предатель", "Доносчик", "Чужак из Блока", "Объедчик", "Подозрительный Тип", "Незнакомец", "Свой со Двора", "Делящий Хлеб", "Старший по Подъезду", "Голос Трущоб", "Барон Хрущёвок"},
        new String[]{"Клеймёная Предательница", "Доносчица", "Чужачка из Блока", "Объедчица", "Подозрительная Особа", "Незнакомка", "Своя со Двора", "Делящая Хлеб", "Старшая по Подъезду", "Голос Трущоб", "Баронесса Хрущёвок"}
    ),
    MIDDLE_CLASS(
        "middle_class",
        "Средний Класс",
        "Рабочие комбинатов, жители типовых панелек и опора планового равенства.",
        0xFF3A9B8C, // Индустриально-бирюзовый
        "§3",       // Тёмно-бирюзовый в чате
        new String[]{"Враг Труда", "Саботажник Нормы", "Лишенец Пая", "Тунеядец", "Безбилетник", "Незнакомец", "Пайщик", "Ударник Смены", "Мастер Цеха", "Стахановец Равенства", "Герой Пятилетки Либры"},
        new String[]{"Врагиня Труда", "Саботажница Нормы", "Лишенка Пая", "Тунеядка", "Безбилетница", "Незнакомка", "Пайщица", "Ударница Смены", "Мастерица Цеха", "Стахановка Равенства", "Героиня Пятилетки Либры"}
    ),
    INTELLIGENTSIA(
        "intelligentsia",
        "Интеллигенция",
        "НИИ Исономии, конструкторы, архивариусы и теоретики идеального баланса.",
        0xFF8A5CF5, // Лазурно-фиолетовый
        "§b",       // Голубой в чате
        new String[]{"Объект Цензуры", "Идейный Вредитель", "Невежда", "Диссидент", "Профан", "Незнакомец", "Слушатель Курсов", "Младший Лаборант", "Инженер Умов", "Архивариус Исономии", "Просветитель Равенства"},
        new String[]{"Объект Цензуры", "Идейная Вредительница", "Невежда", "Диссидентка", "Профанка", "Незнакомка", "Слушательница Курсов", "Младшая Лаборантка", "Инженерка Умов", "Архивариус Исономии", "Просветительница Равенства"}
    ),
    GOVERNMENT(
        "government",
        "Правительство (Наместники Либры)",
        "Номенклатура Барона Контрактов. Закон, безупречные сделки и кара за нарушение.",
        0xFFD4AF37, // Благородное золото Весов
        "§c",       // Багряно-красный в чате
        new String[]{"Расторгнутый Контракт", "Злостный Неплательщик", "Нарушитель Устава", "Должник Весов", "Под Надзором", "Незнакомец", "Учтённый Субъект", "Исполнитель Пункта", "Блюститель Протокола", "Ревизор Контрактов", "Правая Чаша Либры"},
        new String[]{"Расторгнутая Контрактом", "Злостная Неплательщица", "Нарушительница Устава", "Должница Весов", "Под Надзором", "Незнакомка", "Учтённая Субъект", "Исполнительница Пункта", "Блюстительница Протокола", "Ревизорша Контрактов", "Правая Чаша Либры"}
    );

    public static final int[] THRESHOLDS = {-1000, -500, -100, -50, -10, 0, 10, 50, 100, 500, 1000};

    public final String id;
    public final String displayName;
    public final String description;
    public final int color;
    public final String chatColor;
    private final String[] maleTitles;
    private final String[] femaleTitles;

    Faction(String id, String displayName, String description, int color, String chatColor, String[] maleTitles, String[] femaleTitles) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.chatColor = chatColor;
        this.maleTitles = maleTitles;
        this.femaleTitles = femaleTitles;
    }

    public static Faction byId(String id) {
        for (Faction f : values()) {
            if (f.id.equalsIgnoreCase(id)) return f;
        }
        return null;
    }

    public int getTierIndex(int rep) {
        if (rep <= -1000) return 0;
        if (rep <= -500) return 1;
        if (rep <= -100) return 2;
        if (rep <= -50) return 3;
        if (rep <= -10) return 4;
        if (rep >= 1000) return 10;
        if (rep >= 500) return 9;
        if (rep >= 100) return 8;
        if (rep >= 50) return 7;
        if (rep >= 10) return 6;
        return 5; // 0 (Незнакомец)
    }

    public String getTitle(int rep, boolean isFemale) {
        int idx = getTierIndex(rep);
        return isFemale ? femaleTitles[idx] : maleTitles[idx];
    }

    // Возвращает левую и правую границы текущего сегмента (как на твоем эскизе: например -10 и +10, или +10 и +50)
    public int[] getBracketBounds(int rep) {
        if (rep > -10 && rep < 10) return new int[]{-10, 10};
        if (rep >= 10) {
            if (rep < 50) return new int[]{10, 50};
            if (rep < 100) return new int[]{50, 100};
            if (rep < 500) return new int[]{100, 500};
            return new int[]{500, 1000};
        } else {
            if (rep > -50) return new int[]{-50, -10};
            if (rep > -100) return new int[]{-100, -50};
            if (rep > -500) return new int[]{-500, -100};
            return new int[]{-1000, -500};
        }
    }
}
