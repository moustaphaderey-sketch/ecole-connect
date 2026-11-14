package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AboutUsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("about_us_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                AboutUsContent()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun AboutUsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "À PROPOS DE NOUS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("about_us_dialog_content")
            ) {
                AboutUsContent()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("about_us_dialog_close")
            ) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun AboutUsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // En-tête officiel avec le logo + ÉCOLE CONNECTÉE
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(92.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ecole_embleme),
                        contentDescription = "Logo officiel ÉCOLE CONNECTÉE",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Text(
                    text = "À PROPOS DE NOUS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "ÉCOLE CONNECTÉE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Apprendre • Suivre • Réussir ensemble",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Présentation générale
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("ÉCOLE CONNECTÉE")
                        }
                        append(" est une plateforme numérique innovante conçue pour moderniser le système éducatif et renforcer les liens entre les établissements scolaires, les enseignants, les élèves et les parents.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Notre ambition est de rendre le suivi scolaire plus simple, plus transparent et plus accessible grâce aux nouvelles technologies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Notre mission
        AboutSectionCard(
            title = "Notre mission",
            icon = Icons.Filled.Flag
        ) {
            Text(
                text = "Faciliter l'accès aux informations scolaires et permettre à chaque parent de suivre efficacement le parcours éducatif de son enfant, où qu'il soit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Notre vision
        AboutSectionCard(
            title = "Notre vision",
            icon = Icons.Filled.Visibility
        ) {
            Text(
                text = "Construire une école moderne, inclusive et connectée, dans laquelle la technologie devient un outil au service de la réussite de chaque élève.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Nos objectifs
        AboutSectionCard(
            title = "Nos objectifs",
            icon = Icons.Filled.CheckCircle
        ) {
            val objectives = listOf(
                "Digitaliser la gestion des établissements scolaires.",
                "Améliorer la communication entre parents et enseignants.",
                "Faciliter le suivi des notes, absences et retards.",
                "Permettre aux parents de consulter les leçons et devoirs.",
                "Accompagner les enseignants dans le suivi pédagogique.",
                "Renforcer la transparence et la qualité de l'éducation."
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                objectives.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Nos valeurs
        AboutSectionCard(
            title = "Nos valeurs",
            icon = Icons.Filled.Star
        ) {
            val values = listOf(
                "Transparence :" to "rendre les informations scolaires accessibles aux personnes autorisées.",
                "Innovation :" to "utiliser la technologie pour améliorer l'éducation.",
                "Collaboration :" to "rapprocher les familles et les établissements.",
                "Égalité :" to "favoriser un accès équitable aux services numériques.",
                "Sécurité :" to "protéger les informations personnelles et scolaires."
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { (label, description) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(label)
                                }
                                append(" ")
                                append(description)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // À qui s'adresse la plateforme ?
        AboutSectionCard(
            title = "À qui s'adresse la plateforme ?",
            icon = Icons.Filled.Groups
        ) {
            Text(
                text = "ÉCOLE CONNECTÉE s'adresse aux administrations éducatives, aux établissements scolaires, aux directeurs, aux enseignants, aux élèves et aux parents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Notre engagement
        AboutSectionCard(
            title = "Notre engagement",
            icon = Icons.Filled.Handshake
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nous nous engageons à développer une solution fiable, sécurisée et évolutive, adaptée aux besoins réels du monde éducatif.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ÉCOLE CONNECTÉE — Une école plus proche, un avenir plus brillant.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Fondateur et auteur du projet
        OutlinedCard(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Fondateur et auteur du projet",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(
                        text = "Fondateur et auteur du projet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Moustapha Derey dit ABDOURAHMAN DJAMA DHIDAR")
                        }
                        append(" est l'initiateur et le concepteur du projet ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("ÉCOLE CONNECTÉE")
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "À travers cette initiative, il souhaite contribuer à la modernisation du système éducatif en mettant les nouvelles technologies au service des écoles, des enseignants, des élèves et des parents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Son ambition est de faciliter le suivi scolaire, d'améliorer la communication entre les familles et les établissements et de favoriser un environnement éducatif plus moderne, transparent et accessible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Auteur et initiateur du projet : Moustapha Derey",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Projet : ÉCOLE CONNECTÉE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Une école plus proche, un avenir plus brillant.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}
