# Overview
This program converts the rather obscure TKL GPS trace format into the much more portable GPX format.

The TKL format is used by:
* SOMA watches
* Ultrasport Navrun
* Mapjack GPS IQ Watch

# to build
	Eclipse use the jar in /binary

# to run

	java Tkl2Gpx <tklfile> <output folder>

Process one file. Example `java Tkl2Gpx example/20200503094236.tkl example`.
	
	java Tkl2Gpx <folder with tklfile> <output folder>

Process all files in the folder (recurciv) and convert it to output. Example `java Tkl2Gpx example/input-folder example/output-folder`.

# Extensions
Now the converter supports:
- New project structure (with `src`, `gpsmaster`)
- XML namespaces (also valid XML)
- Multiple file convertion
- Heart rate if present (GPX extension)
- Distance and speed (GPX extension)
- The data of the TKL summery is written to the GPX description
- Supports pauses as new trkseg segments (see constant PAUSE_TIME_INDICATOR)
- Better exeption handling with more infos
- Trace which file is processing
- The GPX output can be imported in Runalyze
