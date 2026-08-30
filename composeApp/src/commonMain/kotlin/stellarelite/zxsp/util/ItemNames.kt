package stellarelite.zxsp.util

import stellarelite.zxsp.data.LanguageManager

// 物品中英文映射（费用项 + 食材项），仓库/冰箱/肉品加工/记一笔/报表共用
object ItemNames {
    val EN_MAP: Map<String, String> = mapOf(
        // 费用项（业务开销）
        "员工" to "Staff Salary",
        "老板薪资" to "Boss Salary",
        "租金" to "Shop Rental",
        "卫生纸" to "Toilet Paper",
        "厨具 电器" to "Kitchenware & Appliances",
        "打印纸" to "Printing Paper",
        "杂货" to "Groceries",
        "外卖纸" to "Takeaway Paper",
        // 食材项（进货成本）
        "五花肉" to "Pork Belly",
        "孜然粉" to "Cumin Powder",
        "生抽" to "Light Soy Sauce",
        "烧烤酱" to "BBQ Sauce",
        "牛上脑" to "Beef Chuck",
        "食用油" to "Cooking Oil",
        "羊肩肉" to "Lamb Shoulder",
        "糖" to "Sugar",
        "烧烤撒料（孜然味）" to "BBQ Seasoning (Cumin)",
        "烧烤撒料（香辣味）" to "BBQ Seasoning (Spicy)",
        "辣椒粉" to "Chili Powder",
        "花雕酒" to "Hua Diao Wine",
        "生姜" to "Ginger",
        "蚝油" to "Oyster Sauce",
        "鸡腿肉" to "Chicken Thigh",
        "盐" to "Salt",
        "白胡椒粉" to "White Pepper Powder",
        "炭火" to "Charcoal",
    )

    // 英文界面返回英文名，否则原样返回中文名
    fun display(name: String): String =
        if (LanguageManager.isEnglish) EN_MAP[name] ?: name else name
}
