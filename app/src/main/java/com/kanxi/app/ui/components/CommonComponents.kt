package com.kanxi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kanxi.app.data.CollectionWithItems
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaWithCategory
import java.io.File

@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text("返回")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (actionLabel != null && onAction != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun OperaCard(
    opera: OperaWithCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val largeTextLayout = LocalDensity.current.fontScale >= 1.45f
                if (largeTextLayout) {
                    OperaCardText(
                        opera = opera,
                        showAllText = true,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OperaCover(
                            coverPath = opera.item.coverPath,
                            categoryName = opera.categoryName,
                            title = opera.item.title,
                            decorative = true,
                        )
                        Spacer(Modifier.width(16.dp))
                        OperaCardText(
                            opera = opera,
                            showAllText = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (onPlay != null) {
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text("直接播放《${opera.item.title}》", maxLines = 2)
            }
        }
    }
}

@Composable
private fun OperaCardText(
    opera: OperaWithCategory,
    showAllText: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = opera.item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (showAllText) Int.MAX_VALUE else 3,
            overflow = if (showAllText) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(opera.categoryName)
                if (opera.item.actors.isNotBlank()) append(" · ${opera.item.actors}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (showAllText) Int.MAX_VALUE else 2,
            overflow = if (showAllText) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        if (opera.isFavorite) {
            Text(
                text = "已收藏",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun OperaCover(
    coverPath: String?,
    categoryName: String,
    title: String,
    modifier: Modifier = Modifier,
    decorative: Boolean = false,
) {
    val coverFile = coverPath?.let(::File)?.takeIf(File::isFile)
    if (coverFile != null) {
        AsyncImage(
            model = coverFile,
            contentDescription = if (decorative) null else "$title 的封面",
            modifier = modifier
                .size(width = 112.dp, height = 104.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(width = 112.dp, height = 104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .then(if (decorative) Modifier.clearAndSetSemantics { } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = categoryName.take(2).ifBlank { "戏曲" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun CategoryGrid(
    categories: List<OperaCategory>,
    itemCounts: Map<Long, Int>,
    onCategoryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val oneColumn = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.45f
        val columns = if (oneColumn) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { category ->
                        CategoryCard(
                            category = category,
                            count = itemCounts[category.id] ?: 0,
                            onClick = { onCategoryClick(category.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: OperaCategory,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (count == 0) "暂未添加" else "$count 出戏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun CollectionGrid(
    collections: List<CollectionWithItems>,
    onCollectionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val oneColumn = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.45f
        val columns = if (oneColumn) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            collections.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { collection ->
                        CollectionCard(
                            collection = collection,
                            onClick = { onCollectionClick(collection.collection.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: CollectionWithItems,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = collection.collection.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (collection.itemCount == 0) "暂未添加" else "${collection.itemCount} 出戏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
