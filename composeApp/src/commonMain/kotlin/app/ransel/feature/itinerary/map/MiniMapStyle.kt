package app.ransel.feature.itinerary.map

/*
 * Map styles of the mini map: a quiet map that does not compete with the pins (business POIs and transit hidden, parks and
 * water kept). Starting point from the Rockstar spec "Mini peta di Itinerary"; JSON styling works without a Map ID on
 * Android, iOS and the web, so native maps stay in the free SKU.
 */
internal object MiniMapStyle {
    val light: String = """
        [
          {"elementType": "geometry", "stylers": [{"color": "#EEF3EA"}]},
          {"elementType": "labels.text.fill", "stylers": [{"color": "#525252"}]},
          {"elementType": "labels.text.stroke", "stylers": [{"color": "#FFFFFF"}]},
          {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#CFE6F5"}]},
          {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#FFFFFF"}]},
          {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#DDEBD5"}]},
          {"featureType": "poi.business", "stylers": [{"visibility": "off"}]},
          {"featureType": "transit", "stylers": [{"visibility": "off"}]}
        ]
    """.trimIndent()

    val dark: String = """
        [
          {"elementType": "geometry", "stylers": [{"color": "#1D1D1D"}]},
          {"elementType": "labels.text.fill", "stylers": [{"color": "#C1C1C1"}]},
          {"elementType": "labels.text.stroke", "stylers": [{"color": "#131313"}]},
          {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#0E2A3B"}]},
          {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#3B3B3B"}]},
          {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#173023"}]},
          {"featureType": "poi.business", "stylers": [{"visibility": "off"}]},
          {"featureType": "transit", "stylers": [{"visibility": "off"}]}
        ]
    """.trimIndent()

    fun of(dark: Boolean): String = if (dark) this.dark else light
}
