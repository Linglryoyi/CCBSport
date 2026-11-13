package com.ccbsport.app

import org.json.JSONArray
import org.json.JSONObject

internal object WorkoutJson {
    fun encode(records: List<Workout>): JSONArray = JSONArray().apply {
        records.forEach { record ->
            val route = JSONArray()
            record.points.forEach { route.put(JSONArray().put(it.lat).put(it.lon)) }
            put(JSONObject().put("sport", record.sport.name).put("date", record.date)
                .put("seconds", record.seconds).put("meters", record.meters).put("note", record.note)
                .put("route", route).put("id", record.id).put("steps", record.steps)
                .put("estimatedMeters", record.estimatedMeters))
        }
    }

    fun decode(rows: JSONArray): List<Workout> = List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val route = if (row.has("route")) row.getJSONArray("route") else JSONArray()
        val id = row.getString("id")
        val date = row.getLong("date")
        val seconds = row.getLong("seconds")
        val meters = row.getDouble("meters")
        val steps = if (row.has("steps")) row.getInt("steps") else 0
        val estimate = if (row.has("estimatedMeters")) row.getDouble("estimatedMeters") else 0.0
        require(id.isNotBlank() && date >= 0 && seconds >= 0 && steps >= 0)
        require(meters.isFinite() && meters >= 0 && estimate in 0.0..meters)
        Workout(
            Sport.valueOf(row.getString("sport")), date, seconds, meters, row.optString("note"),
            List(route.length()) { point ->
                val pair = route.getJSONArray(point)
                val lat = pair.getDouble(0)
                val lon = pair.getDouble(1)
                require(lat in -90.0..90.0 && lon in -180.0..180.0)
                RoutePoint(lat, lon)
            }, id, steps, estimate
        )
    }

    fun export(records: List<Workout>): String = JSONObject()
        .put("format", "CCBSport").put("version", 1).put("workouts", encode(records)).toString(2)

    fun import(json: String): List<Workout> {
        val content = json.trimStart('\uFEFF').trim()
        if (content.startsWith("[")) return decode(JSONArray(content))
        val backup = JSONObject(content)
        require(backup.getString("format") == "CCBSport" && backup.getInt("version") == 1)
        return decode(backup.getJSONArray("workouts"))
    }
}
