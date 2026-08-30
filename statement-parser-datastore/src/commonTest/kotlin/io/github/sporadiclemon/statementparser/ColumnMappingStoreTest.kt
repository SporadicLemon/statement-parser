package io.github.sporadiclemon.statementparser

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColumnMappingStoreTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val _data = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = _data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(_data.value)
            _data.value = updated
            return updated
        }
    }

    private val dataStore = FakeDataStore()
    private val store = ColumnMappingStore(dataStore)

    private val mapping = ColumnMapping(
        dateIndex = 0,
        dateFormat = "dd/MM/yyyy",
        amountIndex = 2,
        amountInIndex = null,
        amountOutIndex = null,
        descriptionIndex = 1,
    )

    @Test
    fun `get returns null before any save`() = runTest {
        assertNull(store.get("Monzo").first())
    }

    @Test
    fun `save and get round-trip`() = runTest {
        store.save("Monzo", mapping)
        val retrieved = store.get("Monzo").first()
        assertEquals(mapping, retrieved)
    }

    @Test
    fun `delete removes mapping`() = runTest {
        store.save("Monzo", mapping)
        store.delete("Monzo")
        assertNull(store.get("Monzo").first())
    }

    @Test
    fun `delete only removes specified bank`() = runTest {
        store.save("Monzo", mapping)
        store.save("Starling", mapping.copy(dateIndex = 1))
        store.delete("Monzo")
        assertNull(store.get("Monzo").first())
        assertEquals(mapping.copy(dateIndex = 1), store.get("Starling").first())
    }

    @Test
    fun `save overwrites existing mapping for same bank`() = runTest {
        store.save("Monzo", mapping)
        val updated = mapping.copy(dateIndex = 3)
        store.save("Monzo", updated)
        assertEquals(updated, store.get("Monzo").first())
    }

    @Test
    fun `null optional columns survive round-trip`() = runTest {
        val withNulls = ColumnMapping(
            dateIndex = 0,
            dateFormat = "yyyy-MM-dd",
            amountIndex = null,
            amountInIndex = 3,
            amountOutIndex = 4,
            descriptionIndex = 1,
        )
        store.save("Lloyds", withNulls)
        assertEquals(withNulls, store.get("Lloyds").first())
    }
}
