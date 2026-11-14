package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.DjiboutiSchoolsCatalog
import com.example.data.catalog.OfficialSchoolEntry
import com.example.data.catalog.SchoolLevelFilter

@Composable
fun SchoolTypeBadge(
    schoolType: String,
    modifier: Modifier = Modifier
) {
    val (label, containerColor, contentColor) = when (schoolType.lowercase()) {
        "primaire" -> Triple(
            "Primaire",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        "college" -> Triple(
            "Collège",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        "lycee" -> Triple(
            "Lycée",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Triple(
            schoolType.replaceFirstChar { it.uppercase() },
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SchoolLevelFilterRow(
    selectedFilter: SchoolLevelFilter,
    onSelectFilter: (SchoolLevelFilter) -> Unit,
    countsByLevel: Map<SchoolLevelFilter, Int>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SchoolLevelFilter.entries.forEach { level ->
            val count = countsByLevel?.get(level)
            val label = if (count != null) "${level.labelFr} ($count)" else level.labelFr
            FilterChip(
                selected = selectedFilter == level,
                onClick = { onSelectFilter(level) },
                label = { Text(label) },
                modifier = Modifier.testTag("school_filter_${level.name.lowercase()}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun OfficialSchoolPickerDialog(
    title: String = "Établissements de Djibouti-Ville",
    onDismiss: () -> Unit,
    onSelectSchool: (OfficialSchoolEntry) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(SchoolLevelFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val counts = remember {
        mapOf(
            SchoolLevelFilter.ALL to DjiboutiSchoolsCatalog.allSchools.size,
            SchoolLevelFilter.PRIMAIRE to DjiboutiSchoolsCatalog.primarySchools.size,
            SchoolLevelFilter.COLLEGE to DjiboutiSchoolsCatalog.middleSchools.size,
            SchoolLevelFilter.LYCEE to DjiboutiSchoolsCatalog.highSchools.size
        )
    }

    val filtered = remember(selectedLevel, searchQuery) {
        DjiboutiSchoolsCatalog.filter(selectedLevel, searchQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${DjiboutiSchoolsCatalog.allSchools.size} établissements officiels (Primaire, Collège, Lycée)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Rechercher une école, un collège ou un lycée") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("official_school_search_input")
                )

                SchoolLevelFilterRow(
                    selectedFilter = selectedLevel,
                    onSelectFilter = { selectedLevel = it },
                    countsByLevel = counts
                )

                if (filtered.isEmpty()) {
                    Text(
                        text = "Aucun établissement ne correspond à « $searchQuery ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSchool(entry) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("official_school_item_${entry.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SchoolTypeBadge(schoolType = entry.schoolType)
                                        Text(
                                            text = entry.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${entry.sector} • ${entry.address}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}
