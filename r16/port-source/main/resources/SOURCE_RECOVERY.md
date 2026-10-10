# R16 source recovery

The validated R16 binary was saved, but its temporary working source directory and some newer test files were lost. This release restores source from the preserved R15 source package and the exact saved R16 JAR.

Authoritative saved R16 JAR SHA-256:
`e0dc33647f71ba9ee22856dd9567c3c11ac51bc776473842efbd2c20097a7df1`.

Unchanged Aegis handwritten source is retained from R15. New or modified Aegis classes, the assistant classes and the disabled native command registrar were recovered with Vineflower 1.12.0. Twelve generic declarations were restored to precise collection/UUID types so that recovered source compiles. Compiler/decompiler formatting and local variable names may differ from the original source.

The public 1.21.11 JAR preserves every class from the validated R16 binary. It changes only release metadata and required provenance/license notices. Other public version variants use that runtime, with the version descriptor and compatibility SensorPlugin variant adjusted. Source rebuilds recompile recovered own classes and are not byte-identical runtime reproductions.

The restored builder compiles all 21 variants and runs the original 1,115 sanction/history/TimerSafety assertions plus VPN recovery tests. The VPN recovery suite is also run directly against the saved R16 binary. It covers classification flags, address identity, invalid input, cache expiry/eviction, allowlisting, concurrent lookup coalescing, HTTP errors, response size limits and body deadlines.

Earlier R16 validation recorded 1,307 assertions, legacy-Gson VPN checks and isolated Paper startup. Some original newer test sources and raw startup logs are unavailable. Those results are historical, not claims that these files were recovered or that a new gameplay test was performed.

The source archive includes the pinned baseline JAR, compile-only dependencies, native GPL corresponding source and RuntimePatcher. See the builder and third-party notices for exact inputs. Rebuilt source and compatibility ports still need live validation on the intended server.
