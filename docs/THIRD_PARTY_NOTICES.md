# Third-party notices

Cheshers itself is AGPL-3.0-or-later. The following dependencies retain their own licenses and copyrights. Links point to the upstream projects and their authoritative license texts; package managers also retain license metadata in distributed artifacts.

## Android and JVM

| Component | License | Project / notice |
| --- | --- | --- |
| chesslib 1.3.7 | Apache-2.0 | <https://github.com/bhlangonijr/chesslib> |
| AndroidX Core, Activity, Lifecycle, Navigation, Room, WorkManager, DataStore, Security Crypto, Hilt AndroidX | Apache-2.0 | <https://cs.android.com/androidx/platform/frameworks/support> |
| Jetpack Compose, Material 3, Material core icons | Apache-2.0 | <https://developer.android.com/compose> |
| Kotlin and Kotlin Gradle plugin | Apache-2.0 | <https://github.com/JetBrains/kotlin> |
| kotlinx.coroutines and kotlinx.serialization | Apache-2.0 | <https://github.com/Kotlin/kotlinx.coroutines>, <https://github.com/Kotlin/kotlinx.serialization> |
| Dagger/Hilt | Apache-2.0 | <https://github.com/google/dagger> |
| Gson | Apache-2.0 | <https://github.com/google/gson> |
| Retrofit | Apache-2.0 | <https://github.com/square/retrofit> |
| OkHttp and Okio | Apache-2.0 | <https://github.com/square/okhttp>, <https://github.com/square/okio> |
| Socket.IO Java client / Engine.IO Java client | MIT | <https://github.com/socketio/socket.io-client-java>, <https://github.com/socketio/engine.io-client-java> |
| JUnit 4 (tests) | EPL-1.0 | <https://github.com/junit-team/junit4> |
| AndroidX Test and Espresso (tests) | Apache-2.0 | <https://developer.android.com/testing> |

Apache-2.0 text: <https://www.apache.org/licenses/LICENSE-2.0>. MIT text: <https://opensource.org/license/mit>. EPL-1.0 text: <https://www.eclipse.org/legal/epl-v10.html>.

## Node.js backend direct dependencies

| Component | License | Project |
| --- | --- | --- |
| Express | MIT | <https://github.com/expressjs/express> |
| Socket.IO server and client | MIT | <https://github.com/socketio/socket.io>, <https://github.com/socketio/socket.io-client> |
| better-sqlite3 | MIT | <https://github.com/WiseLibs/better-sqlite3> |
| bcryptjs | BSD-3-Clause | <https://github.com/dcodeIO/bcrypt.js> |
| chess.js | BSD-2-Clause | <https://github.com/jhlywa/chess.js> |
| jsonwebtoken | MIT | <https://github.com/auth0/node-jsonwebtoken> |
| zod | MIT | <https://github.com/colinhacks/zod> |
| Helmet | MIT | <https://github.com/helmetjs/helmet> |
| cors | MIT | <https://github.com/expressjs/cors> |
| express-rate-limit | MIT | <https://github.com/express-rate-limit/express-rate-limit> |
| Supertest (tests) | MIT | <https://github.com/ladjs/supertest> |

BSD-2-Clause text: <https://opensource.org/license/bsd-2-clause>. BSD-3-Clause text: <https://opensource.org/license/bsd-3-clause>.

`backend/package-lock.json` pins the complete backend dependency graph. `gradle/libs.versions.toml` records Android dependency versions. Transitive dependencies remain under their respective licenses; consult the linked lockfiles and upstream package notices when redistributing a combined work.

No Stockfish, Leela, commercial puzzle pack, advertising SDK, analytics SDK, copyrighted piece artwork, or copied Chess.com asset is included.
