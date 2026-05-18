package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sporadiclemon.statementparser.Bank
import io.github.sporadiclemon.statementparser.ParsedStatement
import io.github.sporadiclemon.statementparser.StatementFormat
import io.github.sporadiclemon.statementparser.StatementParser

@Suppress("ktlint:standard:function-naming")
@Composable
fun App(
    onParsePdf: ((ByteArray, String?) -> Result<ParsedStatement>)? = null
) {
    MaterialTheme(colors = lightColors()) {
        val parser = remember { StatementParser() }
        val profiledBanks = remember { parser.getProfiledBanks() }
        var selectedBank by remember { mutableStateOf<Bank?>(null) }
        var showFilePicker by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Result<ParsedStatement>?>(null) }
        var fileName by remember { mutableStateOf("") }
        var detectedFormat by remember { mutableStateOf<StatementFormat?>(null) }

        FilePicker(
            show = showFilePicker,
            onFilePicked = { name, bytes ->
                showFilePicker = false
                fileName = name
                val format = parser.detectFormat(name, "")
                detectedFormat = format
                result =
                    if (format == StatementFormat.PDF) {
                        onParsePdf?.invoke(bytes, selectedBank?.displayName) 
                            ?: parser.parsePdf(bytes, bankHint = selectedBank?.displayName)
                    } else {
                        parser.parse(bytes.decodeToString(), format)
                    }
            },
            onDismiss = { showFilePicker = false },
        )

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F5F5)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Statement Parser") },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White,
                    elevation = 4.dp,
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Add a Statement",
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Select a CSV, OFX or PDF file to parse",
                                style = MaterialTheme.typography.body2,
                                color = Color.Gray,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Bank hint (optional, helps PDF parsing)",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            BankGrid(
                                banks = profiledBanks,
                                selectedBank = selectedBank,
                                onBankSelected = { selectedBank = it },
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showFilePicker = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .fillMaxWidth(0.7f)
                                    .align(Alignment.CenterHorizontally),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select File")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    result?.let { res ->
                        res
                            .onSuccess { statement ->
                                SummarySection(
                                    fileName,
                                    detectedFormat,
                                    statement.transactions.size,
                                    statement.detectedBank?.displayName,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TransactionList(statement)
                            }.onFailure {
                                ErrorDisplay(it.message ?: "Unknown error occurred")
                            }
                    } ?: EmptyState()
                }
            }
        }
    }
}

@Composable
fun BankGrid(
    banks: List<Bank>,
    selectedBank: Bank?,
    onBankSelected: (Bank?) -> Unit,
) {
    val rows = banks.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { bank ->
                    BankChip(
                        bank = bank,
                        selected = bank == selectedBank,
                        modifier = Modifier.weight(1f),
                        onClick = { onBankSelected(if (bank == selectedBank) null else bank) },
                    )
                }
                // Fill remaining slots so chips stay the same width
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BankChip(
    bank: Bank,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) MaterialTheme.colors.primary else Color.White
    val contentColor = if (selected) Color.White else Color(0xFF444444)
    val borderColor = if (selected) MaterialTheme.colors.primary else Color(0xFFDDDDDD)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bank.displayName,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun SummarySection(
    name: String,
    format: StatementFormat?,
    count: Int,
    detectedBank: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.White,
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFFE3F2FD), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1976D2))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val bankLabel = detectedBank?.let { " • $it" } ?: ""
                Text("${format ?: "Unknown"}$bankLabel • $count transactions", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TransactionList(statement: ParsedStatement) {
    Text(
        "Transactions",
        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(statement.transactions) { transaction ->
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = 1.dp,
                backgroundColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(transaction.date.toString(), fontSize = 12.sp, color = Color.Gray)
                        Text(
                            transaction.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                        )
                    }
                    val amount = transaction.amount
                    val color = if (amount < 0) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    Text(
                        "${if (amount > 0) "+" else ""}$amount",
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorDisplay(message: String) {
    Card(
        backgroundColor = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFC62828))
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = Color(0xFFC62828), fontSize = 14.sp)
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.LightGray,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No statement loaded", color = Color.Gray, fontSize = 14.sp)
    }
}
