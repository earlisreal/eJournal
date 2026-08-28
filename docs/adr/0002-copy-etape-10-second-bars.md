# Copy eTape 10-second bars into eJournal

eJournal copies finalized, Position-relevant 10-second OHLCV bars from the local eTape SQLite database into its own market-data store instead of querying eTape while rendering a Position Chart. This duplicates some local data, but preserves historical charts beyond eTape's rolling retention, keeps charts available when eTape is stopped or moved, and avoids coupling rendering to an external live database. Imports are read-only and idempotent, and eTape remains optional.
