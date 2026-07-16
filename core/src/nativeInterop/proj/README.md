# PROJ for iOS

This directory contains PROJ 9.8.1 as a static XCFramework for iOS 14.0+:

- `ios-arm64`: physical iOS devices
- `ios-arm64-simulator`: Apple Silicon iOS Simulator

`proj.db`, `proj.ini`, and PROJ's basic resource files are embedded directly
in each static library. The build disables network, TIFF, and the separately
distributed `proj-data` grids, so the application does not need to copy PROJ
resources into its bundle.

The archive was built from the official release source:

- Source: https://github.com/OSGeo/PROJ/releases/tag/9.8.1
- Source SHA-256: `af5b731c145c1d13c4e3b4eeb7d167e94e845e440f71e3496b4ed8dae0291960`
- Build recipe: `tools/build-proj-ios-xcframework.sh`
- Device library SHA-256: `3d9d81617092c12c5073934ddfed89a9dbbabbbc1d38d0182c5a23b93a4a4b13`
- Simulator library SHA-256: `b5857cf2f092c6450287529bb87f55a931ecae62c3b65220dd2fe40796451418`

See `LICENSE-PROJ.txt`, `NOTICE-EPSG.txt`, and `LICENSE-EPSG.txt` before
redistributing the binary.
