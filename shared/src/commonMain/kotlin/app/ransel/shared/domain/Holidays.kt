package app.ransel.shared.domain

import app.ransel.shared.dto.HolidayDto
import app.ransel.shared.model.HolidayType
import kotlinx.datetime.LocalDate

/*
 * Indonesia's national holidays (tanggal merah) and cuti bersama, from the SKB 3 Menteri (Agama, Ketenagakerjaan,
 * PANRB) as published by setneg.go.id. 2026: SKB 1497/2025, 2/2025, 5/2025 (signed 19 Sep 2025). 2027: SKB 1205/2026,
 * 3/2026, 2/2026 (signed 15 Sep 2026). Bundled so the date picker works offline; GET /content can override it
 * (`ContentDto.holidays`). Add the next year when its SKB is published (usually September). Sundays are not listed:
 * the date picker colours them by itself.
 */

private fun nat(date: String, name: String) = HolidayDto(LocalDate.parse(date), name, HolidayType.NATIONAL)
private fun cuti(date: String, name: String) = HolidayDto(LocalDate.parse(date), name, HolidayType.CUTI_BERSAMA)

val BUNDLED_HOLIDAYS: List<HolidayDto> = listOf(
    // 2026
    nat("2026-01-01", "Tahun Baru 2026 Masehi"),
    nat("2026-01-16", "Isra Mikraj Nabi Muhammad saw."),
    cuti("2026-02-16", "Tahun Baru Imlek 2577 Kongzili"),
    nat("2026-02-17", "Tahun Baru Imlek 2577 Kongzili"),
    cuti("2026-03-18", "Hari Suci Nyepi (Tahun Baru Saka 1948)"),
    nat("2026-03-19", "Hari Suci Nyepi (Tahun Baru Saka 1948)"),
    cuti("2026-03-20", "Idulfitri 1447 H"),
    nat("2026-03-21", "Idulfitri 1447 H"),
    nat("2026-03-22", "Idulfitri 1447 H"),
    cuti("2026-03-23", "Idulfitri 1447 H"),
    cuti("2026-03-24", "Idulfitri 1447 H"),
    nat("2026-04-03", "Wafat Yesus Kristus"),
    nat("2026-04-05", "Kebangkitan Yesus Kristus (Paskah)"),
    nat("2026-05-01", "Hari Buruh Internasional"),
    nat("2026-05-14", "Kenaikan Yesus Kristus"),
    cuti("2026-05-15", "Kenaikan Yesus Kristus"),
    nat("2026-05-27", "Iduladha 1447 H"),
    cuti("2026-05-28", "Iduladha 1447 H"),
    nat("2026-05-31", "Hari Raya Waisak 2570 BE"),
    nat("2026-06-01", "Hari Lahir Pancasila"),
    nat("2026-06-16", "1 Muharam Tahun Baru Islam 1448 H"),
    nat("2026-08-17", "Proklamasi Kemerdekaan"),
    nat("2026-08-25", "Maulid Nabi Muhammad saw."),
    cuti("2026-12-24", "Kelahiran Yesus Kristus"),
    nat("2026-12-25", "Kelahiran Yesus Kristus"),
    // 2027
    nat("2027-01-01", "Tahun Baru 2027 Masehi"),
    nat("2027-01-05", "Isra Mikraj Nabi Muhammad saw."),
    cuti("2027-02-05", "Tahun Baru Imlek 2578 Kongzili"),
    nat("2027-02-06", "Tahun Baru Imlek 2578 Kongzili"),
    nat("2027-03-08", "Hari Suci Nyepi (Tahun Baru Saka 1949)"),
    cuti("2027-03-09", "Idulfitri 1448 H"),
    nat("2027-03-10", "Idulfitri 1448 H"),
    nat("2027-03-11", "Idulfitri 1448 H"),
    cuti("2027-03-12", "Idulfitri 1448 H"),
    cuti("2027-03-15", "Idulfitri 1448 H"),
    cuti("2027-03-25", "Wafat Yesus Kristus"),
    nat("2027-03-26", "Wafat Yesus Kristus"),
    nat("2027-03-28", "Kebangkitan Yesus Kristus (Paskah)"),
    nat("2027-05-01", "Hari Buruh Internasional"),
    nat("2027-05-06", "Kenaikan Yesus Kristus"),
    nat("2027-05-17", "Iduladha 1448 H"),
    cuti("2027-05-18", "Iduladha 1448 H"),
    cuti("2027-05-19", "Hari Raya Waisak 2571 BE"),
    nat("2027-05-20", "Hari Raya Waisak 2571 BE"),
    nat("2027-06-01", "Hari Lahir Pancasila"),
    nat("2027-06-06", "1 Muharam Tahun Baru Islam 1449 H"),
    nat("2027-08-15", "Maulid Nabi Muhammad saw."),
    nat("2027-08-17", "Proklamasi Kemerdekaan"),
    cuti("2027-12-24", "Kelahiran Yesus Kristus"),
    nat("2027-12-25", "Kelahiran Yesus Kristus"),
    nat("2027-12-26", "Isra Mikraj Nabi Muhammad saw."),
)

/**
 * The list the apps use: [remote] (from `GET /content`) replaces the [bundled] days of every year it has a day in,
 * the other years keep the bundled days. Empty [remote] = [bundled] unchanged.
 */
fun mergeHolidays(bundled: List<HolidayDto>, remote: List<HolidayDto>): List<HolidayDto> {
    if (remote.isEmpty()) return bundled
    val years = remote.map { it.date.year }.toSet()
    return bundled.filter { it.date.year !in years } + remote
}

/**
 * Holidays by date. When a date has both kinds, the national holiday wins (it is the tanggal merah). A day listed
 * twice with the same type keeps the first.
 */
fun holidaysByDate(holidays: List<HolidayDto>): Map<LocalDate, HolidayDto> {
    val byDate = LinkedHashMap<LocalDate, HolidayDto>()
    for (h in holidays) {
        val old = byDate[h.date]
        if (old == null || (old.type == HolidayType.CUTI_BERSAMA && h.type == HolidayType.NATIONAL)) byDate[h.date] = h
    }
    return byDate
}
