# Adding content to Rogue Planets

Everything builds into one jar (`jars/RoguePlanets.jar`) and is registered through one plugin
(`roguePlanets.RoguePlanetsModPlugin`). After any change under `src/`, close the game, run `build.bat`, restart.
Data and graphics changes (json, csv, images) only need a game restart. Test generation changes on a NEW campaign.

## New market condition
1. `data/campaign/market_conditions.csv`: add a row (copy an existing one). `script` is the Java class name.
2. `data/campaign/procgen/condition_gen_data.csv`: add a row so the game can show it.
3. Write a class under `src/roguePlanets/econ/conditions/` extending `BaseMarketConditionPlugin`.
4. `graphics/icons/markets/`: add the icon named in the `icon` column.
5. Add the id to `ALL_CUSTOM_CONDITIONS` and write a `generateX()` in `RoguePlanetsModPlugin`
   (most use `scatterCondition(...)` with a small spawn rule). Call it from `generateAllCustomContent()`.
6. Optional description: a `CUSTOM` row in `data/strings/descriptions.csv`.

## New planet type
1. `data/config/planets.json`: add an entry (copy `nocturnal`) with a unique id, texture and `descriptionId`.
2. `graphics/planets/`: add the texture (1024x512 is typical; left and right edges should wrap).
3. `data/strings/descriptions.csv`: add a row `your_id,PLANET,"description text",,,,notes`.
4. Spawning: write a generator in `RoguePlanetsModPlugin` that adds the planet, creates a condition-only market and adds its conditions.

## Rules that avoid breakage
- Every id must be unique across the whole mod and other mods.
- Each generator needs its own once-per-save memory flag.
- Never leave per-frame work in a condition: use a daily `IntervalUtil`, add economy listeners once and let them retire themselves.
- Removing a planet type or condition from the mod breaks saves that already contain it.
