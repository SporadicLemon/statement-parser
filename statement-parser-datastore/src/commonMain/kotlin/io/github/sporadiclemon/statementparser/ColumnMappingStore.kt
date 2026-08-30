package io.github.sporadiclemon.statementparser

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ColumnMappingStore(private val dataStore: DataStore<Preferences>) {

    suspend fun save(bankName: String, mapping: ColumnMapping) {
        dataStore.edit { prefs ->
            prefs[key(bankName, "dateIndex")] = mapping.dateIndex.toString()
            prefs[key(bankName, "dateFormat")] = mapping.dateFormat
            prefs[key(bankName, "amountIndex")] = mapping.amountIndex?.toString() ?: ""
            prefs[key(bankName, "amountInIndex")] = mapping.amountInIndex?.toString() ?: ""
            prefs[key(bankName, "amountOutIndex")] = mapping.amountOutIndex?.toString() ?: ""
            prefs[key(bankName, "descriptionIndex")] = mapping.descriptionIndex.toString()
        }
    }

    fun get(bankName: String): Flow<ColumnMapping?> = dataStore.data.map { prefs ->
        val dateIndex = prefs[key(bankName, "dateIndex")]?.toIntOrNull() ?: return@map null
        val dateFormat = prefs[key(bankName, "dateFormat")]?.takeIf { it.isNotEmpty() } ?: return@map null
        val descriptionIndex = prefs[key(bankName, "descriptionIndex")]?.toIntOrNull() ?: return@map null
        
        ColumnMapping(
            dateIndex = dateIndex,
            dateFormat = dateFormat,
            amountIndex = prefs[key(bankName, "amountIndex")]?.toIntOrNull(),
            amountInIndex = prefs[key(bankName, "amountInIndex")]?.toIntOrNull(),
            amountOutIndex = prefs[key(bankName, "amountOutIndex")]?.toIntOrNull(),
            descriptionIndex = descriptionIndex,
        )
    }

    suspend fun delete(bankName: String) {
        dataStore.edit { prefs ->
            listOf("dateIndex", "dateFormat", "amountIndex", "amountInIndex", "amountOutIndex", "descriptionIndex")
                .forEach { prefs.remove(key(bankName, it)) }
        }
    }

    private fun key(bankName: String, field: String) = stringPreferencesKey("cm_${bankName}_$field")
}
