package mx.kompara.data.accounting

/** A deliberate app/period/field choice. Only a window containing the entire statement can apply it. */
data class StatementSelection(val statementId: String, val fields: Set<MoneyField>)

fun selectStatements(records: List<IncomeRecord>, choices: List<StatementSelection>, window: PeriodWindow): Selection {
    val selected = records.toMutableList()
    val excluded = mutableListOf<IncomeRecord>()
    for (choice in choices) {
        val statement = records.singleOrNull { it.id == choice.statementId } ?: continue
        if (!window.coverage.contains(statement.coverage) || statement.coverage.endInclusive > window.elapsedEnd) continue
        require(statement.source != MoneySource.CAPTURED_ESTIMATE && choice.fields.isNotEmpty())
        val overlaps = selected.filter { it.id != statement.id && it.app == statement.app && it.coverage.overlaps(statement.coverage) }
        // Never replace only a fragment of another aggregate or of an active trip.
        if (overlaps.any { !statement.coverage.contains(it.coverage) || it.inProgress > 0 }) continue
        if (MoneyField.PAYMENT !in choice.fields) continue
        selected.removeAll(overlaps.toSet())
        excluded += overlaps
        selected.removeAll { it.id == statement.id }
        // Each selected field has statement coverage. Missing statement km stay missing, even if
        // captured count happens to match: equal counts alone do not prove identical work.
        selected += statement.copy(
            km = statement.km.takeIf { MoneyField.DISTANCE in choice.fields },
            minutes = statement.minutes.takeIf { MoneyField.HOURS in choice.fields },
            trips = if (MoneyField.TRIPS in choice.fields) statement.trips else 0,
            tripsKnown = MoneyField.TRIPS in choice.fields && statement.tripsKnown,
            fieldSources = choice.fields.associateWith { statement.id },
        )
    }
    val base = AccountingSourceSelector.select(selected)
    return Selection(base.records, excluded + base.excluded)
}
