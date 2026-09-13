# Own tags by portfolio

Tags belong to exactly one Portfolio and can be assigned only to Positions in that Portfolio, replacing the shared global vocabulary so each journal can evolve independently. Existing tags are cloned only into portfolios where they have valid assignments; unused legacy tags are intentionally discarded. Assignments remain keyed by opening transaction ID, and repository validation enforces matching ownership without duplicating `portfolioId` in `PositionTag`.
