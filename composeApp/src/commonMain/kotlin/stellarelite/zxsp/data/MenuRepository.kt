package stellarelite.zxsp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

data class MenuItem(
    val id: Int,
    val name: String,
    val price: Double,
    val category: String,
    val emoji: String = "🍢",
    val image: ImageBitmap? = null
)

object MenuRepository {
    var categories by mutableStateOf(listOf("烧烤"))
        private set

    var items by mutableStateOf(
        listOf(
            MenuItem(1, "羊肉串", 3.00, "烧烤", "🐑"),
            MenuItem(2, "牛肉串", 3.00, "烧烤", "🐮"),
            MenuItem(3, "猪肉串", 3.00, "烧烤", "🐷"),
            MenuItem(4, "鸡肉串", 3.00, "烧烤", "🐔"),
            MenuItem(5, "五花肉串", 3.00, "烧烤", "🥓"),
        )
    )
        private set

    private var nextId = 6

    fun addItem(name: String, price: Double, category: String, image: ImageBitmap?) {
        items = items + MenuItem(nextId++, name, price, category, "🍢", image)
        if (category !in categories) {
            categories = categories + category
        }
    }

    fun updateItem(id: Int, name: String, price: Double, category: String, image: ImageBitmap?) {
        items = items.map {
            if (it.id == id) it.copy(name = name, price = price, category = category, image = image)
            else it
        }
        syncCategories()
    }

    fun removeItem(id: Int) {
        items = items.filter { it.id != id }
        syncCategories()
    }

    private fun syncCategories() {
        val used = items.map { it.category }.toSet()
        categories = categories.filter { it in used } + used.filter { it !in categories }
    }
}
