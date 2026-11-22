package com.falak.livewallpaper

/**
 * Curated offline directory of major cities worldwide so users can select their location
 * manually without granting GPS/location permissions. Users can also enter custom coordinates.
 */
object WorldCities {

    val DEFAULT: PrayerLocation = PrayerLocation(
        id = "amman",
        name = "Amman",
        country = "Jordan",
        latitudeDeg = 31.9500,
        longitudeDeg = 35.9450,
        zoneId = "Asia/Amman"
    )

    val ALL: List<PrayerLocation> = listOf(
        // Jordan & Levant
        DEFAULT,
        PrayerLocation("irbid", "Irbid", "Jordan", 32.5568, 35.8469, "Asia/Amman"),
        PrayerLocation("zarqa", "Zarqa", "Jordan", 32.0728, 36.0880, "Asia/Amman"),
        PrayerLocation("aqaba", "Aqaba", "Jordan", 29.5321, 35.0063, "Asia/Amman"),
        PrayerLocation("jerusalem", "Jerusalem", "Palestine", 31.7683, 35.2137, "Asia/Jerusalem"),
        PrayerLocation("ramallah", "Ramallah", "Palestine", 31.9038, 35.2034, "Asia/Hebron"),
        PrayerLocation("gaza", "Gaza", "Palestine", 31.5017, 34.4668, "Asia/Gaza"),
        PrayerLocation("beirut", "Beirut", "Lebanon", 33.8938, 35.5018, "Asia/Beirut"),
        PrayerLocation("damascus", "Damascus", "Syria", 33.5138, 36.2765, "Asia/Damascus"),
        PrayerLocation("aleppo", "Aleppo", "Syria", 36.2021, 37.1343, "Asia/Damascus"),
        PrayerLocation("baghdad", "Baghdad", "Iraq", 33.3152, 44.3661, "Asia/Baghdad"),
        PrayerLocation("erbil", "Erbil", "Iraq", 36.1911, 44.0092, "Asia/Baghdad"),

        // Arabian Peninsula
        PrayerLocation("makkah", "Makkah", "Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh"),
        PrayerLocation("madinah", "Madinah", "Saudi Arabia", 24.4672, 39.6111, "Asia/Riyadh"),
        PrayerLocation("riyadh", "Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        PrayerLocation("jeddah", "Jeddah", "Saudi Arabia", 21.4858, 39.1925, "Asia/Riyadh"),
        PrayerLocation("dammam", "Dammam", "Saudi Arabia", 26.4207, 50.0888, "Asia/Riyadh"),
        PrayerLocation("dubai", "Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai"),
        PrayerLocation("abu_dhabi", "Abu Dhabi", "UAE", 24.4539, 54.3773, "Asia/Dubai"),
        PrayerLocation("sharjah", "Sharjah", "UAE", 25.3463, 55.4209, "Asia/Dubai"),
        PrayerLocation("doha", "Doha", "Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        PrayerLocation("kuwait_city", "Kuwait City", "Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        PrayerLocation("manama", "Manama", "Bahrain", 26.2285, 50.5860, "Asia/Bahrain"),
        PrayerLocation("muscat", "Muscat", "Oman", 23.5880, 58.3829, "Asia/Muscat"),
        PrayerLocation("sanaa", "Sanaa", "Yemen", 15.3694, 44.1910, "Asia/Aden"),

        // North Africa
        PrayerLocation("cairo", "Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        PrayerLocation("alexandria", "Alexandria", "Egypt", 31.2001, 29.9187, "Africa/Cairo"),
        PrayerLocation("tripoli", "Tripoli", "Libya", 32.8872, 13.1913, "Africa/Tripoli"),
        PrayerLocation("tunis", "Tunis", "Tunisia", 36.8065, 10.1815, "Africa/Tunis"),
        PrayerLocation("algiers", "Algiers", "Algeria", 36.7538, 3.0588, "Africa/Algiers"),
        PrayerLocation("casablanca", "Casablanca", "Morocco", 33.5731, -7.5898, "Africa/Casablanca"),
        PrayerLocation("rabat", "Rabat", "Morocco", 34.0209, -6.8416, "Africa/Casablanca"),
        PrayerLocation("marrakech", "Marrakech", "Morocco", 31.6295, -7.9811, "Africa/Casablanca"),
        PrayerLocation("khartoum", "Khartoum", "Sudan", 15.5007, 32.5599, "Africa/Khartoum"),

        // Sub-Saharan Africa
        PrayerLocation("lagos", "Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        PrayerLocation("abuja", "Abuja", "Nigeria", 9.0765, 7.3986, "Africa/Lagos"),
        PrayerLocation("dakar", "Dakar", "Senegal", 14.7167, -17.4677, "Africa/Dakar"),
        PrayerLocation("nairobi", "Nairobi", "Kenya", -1.2921, 36.8219, "Africa/Nairobi"),
        PrayerLocation("mogadishu", "Mogadishu", "Somalia", 2.0469, 45.3182, "Africa/Mogadishu"),
        PrayerLocation("addis_ababa", "Addis Ababa", "Ethiopia", 8.9806, 38.7578, "Africa/Addis_Ababa"),
        PrayerLocation("johannesburg", "Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        PrayerLocation("cape_town", "Cape Town", "South Africa", -33.9249, 18.4241, "Africa/Johannesburg"),

        // Turkey, Caucasus & Central Asia
        PrayerLocation("istanbul", "Istanbul", "Turkey", 41.0082, 28.9784, "Europe/Istanbul"),
        PrayerLocation("ankara", "Ankara", "Turkey", 39.9334, 32.8597, "Europe/Istanbul"),
        PrayerLocation("izmir", "Izmir", "Turkey", 38.4237, 27.1428, "Europe/Istanbul"),
        PrayerLocation("baku", "Baku", "Azerbaijan", 40.4093, 49.8671, "Asia/Baku"),
        PrayerLocation("tehran", "Tehran", "Iran", 35.6892, 51.3890, "Asia/Tehran"),
        PrayerLocation("tashkent", "Tashkent", "Uzbekistan", 41.2995, 69.2401, "Asia/Tashkent"),
        PrayerLocation("almaty", "Almaty", "Kazakhstan", 43.2389, 76.8897, "Asia/Almaty"),

        // South Asia
        PrayerLocation("islamabad", "Islamabad", "Pakistan", 33.6844, 73.0479, "Asia/Karachi"),
        PrayerLocation("karachi", "Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        PrayerLocation("lahore", "Lahore", "Pakistan", 31.5204, 74.3587, "Asia/Karachi"),
        PrayerLocation("peshawar", "Peshawar", "Pakistan", 34.0151, 71.5249, "Asia/Karachi"),
        PrayerLocation("dhaka", "Dhaka", "Bangladesh", 23.8103, 90.4125, "Asia/Dhaka"),
        PrayerLocation("new_delhi", "New Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
        PrayerLocation("mumbai", "Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        PrayerLocation("hyderabad", "Hyderabad", "India", 17.3850, 78.4867, "Asia/Kolkata"),
        PrayerLocation("bengaluru", "Bengaluru", "India", 12.9716, 77.5946, "Asia/Kolkata"),
        PrayerLocation("kolkata", "Kolkata", "India", 22.5726, 88.3639, "Asia/Kolkata"),
        PrayerLocation("srinagar", "Srinagar", "India", 34.0837, 74.7973, "Asia/Kolkata"),
        PrayerLocation("colombo", "Colombo", "Sri Lanka", 6.9271, 79.8612, "Asia/Colombo"),
        PrayerLocation("kabul", "Kabul", "Afghanistan", 34.5553, 69.2075, "Asia/Kabul"),

        // Southeast & East Asia
        PrayerLocation("kuala_lumpur", "Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        PrayerLocation("singapore", "Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        PrayerLocation("jakarta", "Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        PrayerLocation("surabaya", "Surabaya", "Indonesia", -7.2575, 112.7521, "Asia/Jakarta"),
        PrayerLocation("bandung", "Bandung", "Indonesia", -6.9175, 107.6191, "Asia/Jakarta"),
        PrayerLocation("medan", "Medan", "Indonesia", 3.5952, 98.6722, "Asia/Jakarta"),
        PrayerLocation("makassar", "Makassar", "Indonesia", -5.1477, 119.4327, "Asia/Makassar"),
        PrayerLocation("bangkok", "Bangkok", "Thailand", 13.7563, 100.5018, "Asia/Bangkok"),
        PrayerLocation("manila", "Manila", "Philippines", 14.5995, 120.9842, "Asia/Manila"),
        PrayerLocation("tokyo", "Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
        PrayerLocation("osaka", "Osaka", "Japan", 34.6937, 135.5023, "Asia/Tokyo"),
        PrayerLocation("seoul", "Seoul", "South Korea", 37.5665, 126.9780, "Asia/Seoul"),
        PrayerLocation("beijing", "Beijing", "China", 39.9042, 116.4074, "Asia/Shanghai"),
        PrayerLocation("shanghai", "Shanghai", "China", 31.2304, 121.4737, "Asia/Shanghai"),
        PrayerLocation("hong_kong", "Hong Kong", "China", 22.3193, 114.1694, "Asia/Hong_Kong"),

        // Europe
        PrayerLocation("london", "London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        PrayerLocation("birmingham", "Birmingham", "United Kingdom", 52.4862, -1.8904, "Europe/London"),
        PrayerLocation("manchester", "Manchester", "United Kingdom", 53.4808, -2.2426, "Europe/London"),
        PrayerLocation("edinburgh", "Edinburgh", "United Kingdom", 55.9533, -3.1883, "Europe/London"),
        PrayerLocation("dublin", "Dublin", "Ireland", 53.3498, -6.2603, "Europe/Dublin"),
        PrayerLocation("paris", "Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        PrayerLocation("marseille", "Marseille", "France", 43.2965, 5.3698, "Europe/Paris"),
        PrayerLocation("lyon", "Lyon", "France", 45.7640, 4.8357, "Europe/Paris"),
        PrayerLocation("brussels", "Brussels", "Belgium", 50.8503, 4.3517, "Europe/Brussels"),
        PrayerLocation("amsterdam", "Amsterdam", "Netherlands", 52.3676, 4.9041, "Europe/Amsterdam"),
        PrayerLocation("rotterdam", "Rotterdam", "Netherlands", 51.9244, 4.4777, "Europe/Amsterdam"),
        PrayerLocation("berlin", "Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        PrayerLocation("munich", "Munich", "Germany", 48.1351, 11.5820, "Europe/Berlin"),
        PrayerLocation("frankfurt", "Frankfurt", "Germany", 50.1109, 8.6821, "Europe/Berlin"),
        PrayerLocation("hamburg", "Hamburg", "Germany", 53.5511, 9.9937, "Europe/Berlin"),
        PrayerLocation("zurich", "Zurich", "Switzerland", 47.3769, 8.5417, "Europe/Zurich"),
        PrayerLocation("vienna", "Vienna", "Austria", 48.2082, 16.3738, "Europe/Vienna"),
        PrayerLocation("madrid", "Madrid", "Spain", 40.4168, -3.7038, "Europe/Madrid"),
        PrayerLocation("barcelona", "Barcelona", "Spain", 41.3874, 2.1686, "Europe/Madrid"),
        PrayerLocation("rome", "Rome", "Italy", 41.9028, 12.4964, "Europe/Rome"),
        PrayerLocation("milan", "Milan", "Italy", 45.4642, 9.1900, "Europe/Rome"),
        PrayerLocation("stockholm", "Stockholm", "Sweden", 59.3293, 18.0686, "Europe/Stockholm"),
        PrayerLocation("oslo", "Oslo", "Norway", 59.9139, 10.7522, "Europe/Oslo"),
        PrayerLocation("copenhagen", "Copenhagen", "Denmark", 55.6761, 12.5683, "Europe/Copenhagen"),
        PrayerLocation("helsinki", "Helsinki", "Finland", 60.1699, 24.9384, "Europe/Helsinki"),
        PrayerLocation("warsaw", "Warsaw", "Poland", 52.2297, 21.0122, "Europe/Warsaw"),
        PrayerLocation("athens", "Athens", "Greece", 37.9838, 23.7275, "Europe/Athens"),
        PrayerLocation("sarajevo", "Sarajevo", "Bosnia & Herzegovina", 43.8563, 18.4131, "Europe/Sarajevo"),
        PrayerLocation("moscow", "Moscow", "Russia", 55.7558, 37.6173, "Europe/Moscow"),
        PrayerLocation("reykjavik", "Reykjavik", "Iceland", 64.1466, -21.9426, "Atlantic/Reykjavik"),

        // North America
        PrayerLocation("new_york", "New York", "USA", 40.7128, -74.0060, "America/New_York"),
        PrayerLocation("washington_dc", "Washington, D.C.", "USA", 38.9072, -77.0369, "America/New_York"),
        PrayerLocation("boston", "Boston", "USA", 42.3601, -71.0589, "America/New_York"),
        PrayerLocation("philadelphia", "Philadelphia", "USA", 39.9526, -75.1652, "America/New_York"),
        PrayerLocation("chicago", "Chicago", "USA", 41.8781, -87.6298, "America/Chicago"),
        PrayerLocation("detroit", "Detroit", "USA", 42.3314, -83.0458, "America/Detroit"),
        PrayerLocation("dearborn", "Dearborn", "USA", 42.3223, -83.1763, "America/Detroit"),
        PrayerLocation("houston", "Houston", "USA", 29.7604, -95.3698, "America/Chicago"),
        PrayerLocation("dallas", "Dallas", "USA", 32.7767, -96.7970, "America/Chicago"),
        PrayerLocation("atlanta", "Atlanta", "USA", 33.7490, -84.3880, "America/New_York"),
        PrayerLocation("miami", "Miami", "USA", 25.7617, -80.1918, "America/New_York"),
        PrayerLocation("minneapolis", "Minneapolis", "USA", 44.9778, -93.2650, "America/Chicago"),
        PrayerLocation("denver", "Denver", "USA", 39.7392, -104.9903, "America/Denver"),
        PrayerLocation("phoenix", "Phoenix", "USA", 33.4484, -112.0740, "America/Phoenix"),
        PrayerLocation("los_angeles", "Los Angeles", "USA", 34.0522, -118.2437, "America/Los_Angeles"),
        PrayerLocation("san_francisco", "San Francisco", "USA", 37.7749, -122.4194, "America/Los_Angeles"),
        PrayerLocation("san_jose", "San Jose", "USA", 37.3382, -121.8863, "America/Los_Angeles"),
        PrayerLocation("seattle", "Seattle", "USA", 47.6062, -122.3321, "America/Los_Angeles"),
        PrayerLocation("honolulu", "Honolulu", "USA", 21.3069, -157.8583, "Pacific/Honolulu"),
        PrayerLocation("toronto", "Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        PrayerLocation("montreal", "Montreal", "Canada", 45.5017, -73.5673, "America/Toronto"),
        PrayerLocation("ottawa", "Ottawa", "Canada", 45.4215, -75.6972, "America/Toronto"),
        PrayerLocation("vancouver", "Vancouver", "Canada", 49.2827, -123.1207, "America/Vancouver"),
        PrayerLocation("calgary", "Calgary", "Canada", 51.0447, -114.0719, "America/Edmonton"),
        PrayerLocation("edmonton", "Edmonton", "Canada", 53.5461, -113.4938, "America/Edmonton"),
        PrayerLocation("mexico_city", "Mexico City", "Mexico", 19.4326, -99.1332, "America/Mexico_City"),

        // South America
        PrayerLocation("sao_paulo", "São Paulo", "Brazil", -23.5505, -46.6333, "America/Sao_Paulo"),
        PrayerLocation("buenos_aires", "Buenos Aires", "Argentina", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"),
        PrayerLocation("bogota", "Bogotá", "Colombia", 4.7110, -74.0721, "America/Bogota"),
        PrayerLocation("santiago", "Santiago", "Chile", -33.4489, -70.6693, "America/Santiago"),
        PrayerLocation("lima", "Lima", "Peru", -12.0464, -77.0428, "America/Lima"),

        // Oceania
        PrayerLocation("sydney", "Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
        PrayerLocation("melbourne", "Melbourne", "Australia", -37.8136, 144.9631, "Australia/Melbourne"),
        PrayerLocation("brisbane", "Brisbane", "Australia", -27.4698, 153.0251, "Australia/Brisbane"),
        PrayerLocation("perth", "Perth", "Australia", -31.9505, 115.8605, "Australia/Perth"),
        PrayerLocation("adelaide", "Adelaide", "Australia", -34.9285, 138.6007, "Australia/Adelaide"),
        PrayerLocation("auckland", "Auckland", "New Zealand", -36.8509, 174.7645, "Pacific/Auckland"),
        PrayerLocation("wellington", "Wellington", "New Zealand", -41.2865, 174.7762, "Pacific/Auckland")
    )

    fun filter(query: String): List<PrayerLocation> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL
        return ALL.filter { city ->
            city.name.lowercase().contains(q) ||
                city.country.lowercase().contains(q) ||
                city.zoneId.lowercase().contains(q)
        }
    }

    fun findById(id: String?): PrayerLocation? {
        if (id == null) return null
        return ALL.firstOrNull { it.id == id }
    }
}
