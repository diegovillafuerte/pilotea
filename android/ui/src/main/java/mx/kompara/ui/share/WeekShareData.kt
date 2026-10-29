package mx.kompara.ui.share

import mx.kompara.data.accounting.PeriodMoney

fun weekShareData(money: PeriodMoney, hide: Boolean): ShareCardData =
    ShareCardComposer.compose(money, SharePeriodKind.WEEK, hide)
