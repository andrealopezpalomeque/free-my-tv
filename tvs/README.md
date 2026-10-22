# One settings file per TV

Each TV has its own `<name>.properties` here: its city, its TMDB key and which features are on. They stay on this
computer (git ignores them, since they hold the TMDB key). The code is the same for every TV.

```bash
./build.sh --config tvs/piso-tv.properties            # Google TV / Android TV
python3 webos/build.py --config tvs/piso-tv.properties  # LG
python3 tizen/build.py --config tvs/piso-tv.properties  # Samsung
```

A new TV: build with a new name (`--config tvs/<name>.properties`) and the first build creates the file from
`config.example.properties`; or copy another TV's file and change what differs. `config.properties` still works
when no `--config` is given.
