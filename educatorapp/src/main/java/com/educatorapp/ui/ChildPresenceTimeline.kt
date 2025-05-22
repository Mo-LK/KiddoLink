package com.educatorapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.educatorapp.models.PresenceReport

@Composable
fun ChildPresenceTimeline(
    reports: List<PresenceReport>,
    modifier: Modifier = Modifier
) {
    val hours = buildList {
        val start = 7 * 60
        val end = 17 * 60
        for (minute in start..end step 5) {
            val h = minute / 60
            val m = minute % 60
            add("%02d:%02d".format(h, m))
        }
    }

    val scrollState = rememberScrollState()
    val barWidth = 36.dp
    val barSpacing = 4.dp

    val validTimes = hours.toSet()
    val groupedReports = reports
        .map { it.copy(time = it.time.trim()) }
        .filter { it.time in validTimes }
        .groupBy { it.deviceId }

    Column(modifier = modifier.padding(16.dp)) {
        groupedReports.forEach { (childId, childReports) ->
            Text("Child: $childId", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(bottom = 8.dp)
            ) {
                hours.forEach { time ->
                    val report = childReports.find { it.time == time }
                    val color = when (report?.isAlone) {
                        true -> Color.Red
                        false -> Color.Green
                        else -> Color.LightGray
                    }
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(24.dp)
                            .background(color)
                    )
                    Spacer(Modifier.width(barSpacing))
                }
            }
        }

        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            hours.forEach { time ->
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (time.endsWith(":00")) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.width(barSpacing))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Daily Summary", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        val summary = groupedReports.map { (childId, childReports) ->
            val total = childReports.size
            val aloneCount = childReports.count { it.isAlone }
            val percent = if (total > 0) (aloneCount * 100 / total) else 0
            Triple(childId, aloneCount, total to percent)
        }

        Row(Modifier.fillMaxWidth()) {
            Text("Child ID", Modifier.weight(1f))
            Text("Alone %", Modifier.weight(1f))
            Text("Total", Modifier.weight(1f))
            Text("Alone", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        summary.forEach { (id, alone, totalAndPercent) ->
            val (total, percent) = totalAndPercent
            Row(Modifier.fillMaxWidth()) {
                Text(id, Modifier.weight(1f))
                Text("$percent%", Modifier.weight(1f))
                Text("$total", Modifier.weight(1f))
                Text("$alone", Modifier.weight(1f))
            }
        }
    }
}
