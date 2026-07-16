# Third-party notices

Tilo Compose uses different coordinate-transformation engines on each platform.

| Platform | Component | Version | License / terms |
| --- | --- | --- | --- |
| Android | LocationTech Proj4J and `proj4j-epsg` | 1.4.1 | Apache License 2.0, included PROJ-derived code under MIT, and EPSG Dataset Terms of Use |
| iOS | OSGeo PROJ | 9.8.1 | PROJ MIT-style license and EPSG Dataset Terms of Use |

The applicable texts are embedded in the distributed platform artifacts:

- Android AAR: `META-INF/third-party/LICENSE-PROJ4J.txt` and `LICENSE-EPSG.txt`
- iOS cinterop KLIB: `default/resources/META-INF/third-party/`
- PROJ XCFramework: `LICENSE-PROJ.txt`, `NOTICE-EPSG.txt`, and `LICENSE-EPSG.txt`

The EPSG Dataset is owned and maintained by the International Association of
Oil & Gas Producers (IOGP). Tilo is an independent value-added product and is
not endorsed by, affiliated with, or maintained by IOGP or the EPSG Geodetic
Parameter Dataset.

Upstream sources:

- https://github.com/locationtech/proj4j/tree/v1.4.1
- https://github.com/OSGeo/PROJ/releases/tag/9.8.1
- https://epsg.org/terms-of-use.html
