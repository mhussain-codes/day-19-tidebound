# Tidebound: Signal Keeper

A small original lighthouse puzzle game about routing a signal home. Rotate the harbor's mirrors, steer one beam around the reefs, and guide each lost boat to its dock. Eight hand-built channels start simple and grow into tighter routes.

This is a Java desktop game made with Swing. It saves cleared levels, best scores, and every signal attempt in a local SQLite database. There is no account, web server, or online leaderboard.

## Play

1. Install a Java 21 JDK.
2. Run `gradlew.bat run` on Windows, or `./gradlew run` on macOS/Linux.
3. Click the glowing mirror tiles to rotate them between `/` and `\`.
4. Select **Send the signal** to trace the beam. Reefs block it; the dock catches it.
5. Earn up to three stars, then open the next channel. A hint aligns one mirror and costs score.

Progress is saved to `~/.tidebound/tidebound.db` (`%USERPROFILE%\.tidebound\tidebound.db` on Windows). The game creates the database and its tables on first launch.

## Rules and scoring

- The beam leaves the lighthouse heading east.
- A `/` or `\` mirror reflects the signal according to the usual reflection rules.
- Reefs stop the beam. A route that exits the chart or loops does not reach the harbor.
- Each cleared channel records mirror turns, hints, score, and time. Best score and unlocked channels persist in SQLite.
- Signal score is `max(0, 100 - 7 × mirror turns - 18 × hints)`. Three stars start at 86 points; two start at 65.

## Project structure

```text
src/main/java/com/hussaincodes/tidebound/
  TideboundApp.java   Swing interface, board drawing, input, and signal tracing
  Puzzle.java         Hand-built routes, reef maps, and mirror solutions
  GameStore.java      SQLite schema and transactional progress writes
```

## Build a runnable package

```shell
./gradlew build
```

On Windows, use `gradlew.bat build`. Gradle resolves the SQLite JDBC driver from Maven Central. The application can then be launched with `./gradlew run` or `gradlew.bat run`.

## Database

SQLite creates two tables locally:

- `level_progress` keeps the highest score and latest clear time for each channel.
- `attempts` records wins and misses, mirror turns, hints, score, and timestamp.

Each attempt is written in a transaction. The database can be deleted to reset local progress.

## License

MIT. See [LICENSE](LICENSE).
