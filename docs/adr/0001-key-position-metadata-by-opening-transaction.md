# Key position metadata by opening transaction

Position Notes use a position's opening transaction ID, matching existing tags, instead of introducing a persisted Position identity. Positions remain derived from transactions and the metadata model stays small; importing earlier history may change position boundaries and detach metadata, which is an accepted trade-off until backfilling after journaling becomes a real workflow.
