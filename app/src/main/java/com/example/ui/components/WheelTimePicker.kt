package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.NightSurfaceHighest
import com.example.ui.theme.StarlightMist
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Consumes all residual vertical scroll and fling deltas so dragging/swiping downward
 * inside a wheel picker NEVER leaks upward to drag or dismiss a parent ModalBottomSheet
 * or outer scroll container.
 */
private val ConsumeResidualVerticalNestedScroll = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = Offset(x = 0f, y = available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y)
}

/**
 * Smooth snapping scroll-wheel time picker (Hour 1–12, Minute 00–59, AM/PM).
 */
@Composable
fun WheelTimePicker(
    hour24: Int,
    minute: Int,
    isActiveMode: Boolean,
    onTimeChanged: (hour24: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = remember { (1..12).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val amPmItems = remember { listOf("AM", "PM") }

    val latestOnTimeChanged by rememberUpdatedState(onTimeChanged)

    var selectedHour12Idx by remember {
        mutableIntStateOf(((hour24 % 12).let { if (it == 0) 12 else it } - 1).coerceIn(0, 11))
    }
    var selectedMinuteIdx by remember {
        mutableIntStateOf(minute.coerceIn(0, 59))
    }
    var selectedAmPmIdx by remember {
        mutableIntStateOf(if (hour24 >= 12) 1 else 0)
    }

    LaunchedEffect(hour24, minute) {
        val extHour12Idx = ((hour24 % 12).let { if (it == 0) 12 else it } - 1).coerceIn(0, 11)
        val extMinIdx = minute.coerceIn(0, 59)
        val extAmPmIdx = if (hour24 >= 12) 1 else 0
        if (extHour12Idx != selectedHour12Idx) selectedHour12Idx = extHour12Idx
        if (extMinIdx != selectedMinuteIdx) selectedMinuteIdx = extMinIdx
        if (extAmPmIdx != selectedAmPmIdx) selectedAmPmIdx = extAmPmIdx
    }

    fun emitCurrent(hIdx: Int, mIdx: Int, apIdx: Int) {
        val h12 = hIdx + 1
        val pm = apIdx == 1
        val h24 = when {
            !pm && h12 == 12 -> 0
            pm && h12 == 12 -> 12
            pm -> h12 + 12
            else -> h12
        }
        latestOnTimeChanged(h24, mIdx)
    }

    val itemHeight = 44.dp
    val wheelHeight = itemHeight * 3

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(wheelHeight)
            .nestedScroll(ConsumeResidualVerticalNestedScroll),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            shape = RoundedCornerShape(14.dp),
            color = if (isActiveMode) {
                DawnGold.copy(alpha = 0.14f)
            } else {
                NightSurfaceHighest.copy(alpha = 0.45f)
            },
            border = BorderStroke(
                1.dp,
                if (isActiveMode) DawnGold.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        ) {}

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnapWheelColumn(
                items = hours,
                selectedIndex = selectedHour12Idx,
                itemHeight = itemHeight,
                accentColor = if (isActiveMode) DawnGoldBright else StarlightMist,
                onSelectedIndexChanged = { newIdx ->
                    if (newIdx != selectedHour12Idx) {
                        selectedHour12Idx = newIdx
                        emitCurrent(selectedHour12Idx, selectedMinuteIdx, selectedAmPmIdx)
                    }
                },
                modifier = Modifier
                    .width(78.dp)
                    .testTag("wheel_hour_picker")
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isActiveMode) DawnGoldBright else CloudSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SnapWheelColumn(
                items = minutes,
                selectedIndex = selectedMinuteIdx,
                itemHeight = itemHeight,
                accentColor = if (isActiveMode) DawnGoldBright else StarlightMist,
                onSelectedIndexChanged = { newIdx ->
                    if (newIdx != selectedMinuteIdx) {
                        selectedMinuteIdx = newIdx
                        emitCurrent(selectedHour12Idx, selectedMinuteIdx, selectedAmPmIdx)
                    }
                },
                modifier = Modifier
                    .width(78.dp)
                    .testTag("wheel_minute_picker")
            )

            Box(modifier = Modifier.width(12.dp))

            SnapWheelColumn(
                items = amPmItems,
                selectedIndex = selectedAmPmIdx,
                itemHeight = itemHeight,
                accentColor = if (isActiveMode) DawnGoldBright else StarlightMist,
                onSelectedIndexChanged = { newIdx ->
                    if (newIdx != selectedAmPmIdx) {
                        selectedAmPmIdx = newIdx
                        emitCurrent(selectedHour12Idx, selectedMinuteIdx, selectedAmPmIdx)
                    }
                },
                modifier = Modifier
                    .width(72.dp)
                    .testTag("wheel_ampm_picker")
            )
        }
    }
}

/**
 * Reusable numeric scroll-wheel with parent vertical swipe interception protection
 * and customizable label formatting (e.g., formatting 60 as "1 hour").
 */
@Composable
fun NumericWheelPicker(
    range: IntRange,
    selectedValue: Int,
    unitSuffix: String,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelFormatter: (Int) -> String = { "$it $unitSuffix" }
) {
    val items = remember(range, unitSuffix, labelFormatter) {
        range.map { labelFormatter(it) }
    }
    val latestOnValueSelected by rememberUpdatedState(onValueSelected)
    var currentIndex by remember(range) {
        mutableIntStateOf((selectedValue - range.first).coerceIn(0, items.lastIndex))
    }

    LaunchedEffect(selectedValue, range) {
        val extIdx = (selectedValue - range.first).coerceIn(0, items.lastIndex)
        if (extIdx != currentIndex) {
            currentIndex = extIdx
        }
    }

    val itemHeight = 44.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * 3)
            .nestedScroll(ConsumeResidualVerticalNestedScroll),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(itemHeight),
            shape = RoundedCornerShape(14.dp),
            color = DawnGold.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.55f))
        ) {}

        SnapWheelColumn(
            items = items,
            selectedIndex = currentIndex,
            itemHeight = itemHeight,
            accentColor = DawnGoldBright,
            onSelectedIndexChanged = { idx ->
                if (idx != currentIndex) {
                    currentIndex = idx
                    latestOnValueSelected(range.first + idx)
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .testTag("snooze_custom_wheel")
        )
    }
}

@Composable
private fun SnapWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    itemHeight: Dp,
    accentColor: Color,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedInitial = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = clampedInitial)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val latestOnSelected by rememberUpdatedState(onSelectedIndexChanged)

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex)
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visible.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index?.coerceIn(0, items.lastIndex) ?: listState.firstVisibleItemIndex
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.coerceIn(0, items.lastIndex)
        if (!listState.isScrollInProgress && centeredIndex != target) {
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState) {
        var sawScroll = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (inProgress) {
                    sawScroll = true
                } else if (sawScroll) {
                    val settled = centeredIndex.coerceIn(0, items.lastIndex)
                    latestOnSelected(settled)
                }
            }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(itemHeight * 3)
            .nestedScroll(ConsumeResidualVerticalNestedScroll)
    ) {
        items(items.size) { index ->
            val isCentered = index == centeredIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        latestOnSelected(index)
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = items[index],
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = if (isCentered) 24.sp else 18.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCentered) accentColor else CloudSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(if (isCentered) 1f else 0.45f)
                )
            }
        }
    }
}
