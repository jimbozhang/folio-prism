package com.jimbozhang.folioprism.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jimbozhang.folioprism.domain.calc.PortfolioSnapshot
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.calc.Valuation
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.goldPremium
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.money.toCnyAmount
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.ui.theme.FolioInk
import java.math.BigDecimal

@Composable
fun HoldingRow(
    data: PortfolioData,
    quotes: QuoteSnapshot,
    snapshot: PortfolioSnapshot,
    key: PositionKey,
    quantity: BigDecimal,
) {
    val instrument = data.instrument(key.instrumentId) ?: return
    val institutionName = data.institutions.firstOrNull { it.id == key.institutionId }?.name
        ?: key.institutionId
    val quotePrice = quotes.quotes[instrument.id]?.price ?: instrument.quote.manualPrice
    val price = if (instrument.kind == InstrumentKind.QUOTED && quotePrice != null) {
        if (Valuation.isGold(instrument)) quotePrice.add(data.goldPremium(key.institutionId))
        else quotePrice
    } else {
        null
    }
    val nativeValue = when {
        instrument.kind == InstrumentKind.CASH -> quantity
        price != null -> {
            val raw = quantity.multiply(price)
            // A CNY-denominated market value is a CNY amount: round to the fen.
            if (instrument.currency.uppercase() in setOf("CNY", "CNH")) raw.toCnyAmount() else raw
        }
        else -> null
    }
    val valueText = if (nativeValue == null) {
        "--"
    } else {
        formatNative(nativeValue, instrument.currency)
    }

    Column(modifier = Modifier.padding(vertical = 9.dp)) {
        Text(
            text = "${instrument.code} ${instrument.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = FolioInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$institutionName · ${formatQuantity(quantity)} ${instrument.unit}" +
                    (price?.let { " × " + formatNative(it, instrument.currency) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
