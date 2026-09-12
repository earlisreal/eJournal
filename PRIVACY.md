# Privacy policy

Effective: 2026-09-13

eJournal is a local-first desktop application. The eJournal project does not operate an application server, create user accounts, collect telemetry, serve advertising, or receive users' journal data. The application makes the network requests described below.

## Data stored locally

eJournal stores imported transactions, portfolios, settings, notes, tags, and cached market data under the user's home directory in `~/.ejournal`. Broker API credentials are stored in `~/.ejournal/credentials.json`. Files selected for import are read locally and are not uploaded by eJournal.

Uninstalling the application does not delete this data. To remove it, close eJournal and delete the `.ejournal` directory from the user's home directory. This permanently removes the journal database, settings stored there, cached market data, and saved broker credentials.

## Network connections

- **Yahoo Finance market data:** eJournal automatically requests market data after imports and during startup synchronization when stored symbols need data. Requests send ticker symbols, requested date ranges, and ordinary network metadata such as the user's IP address. They do not send transaction quantities, prices, profit and loss, notes, tags, or broker credentials. The current application has no separate switch for Yahoo requests. See [Yahoo's privacy policy](https://legal.yahoo.com/us/en/yahoo/privacy/index.html).
- **Alpaca:** This integration is optional and user-configured. Testing credentials, synchronizing an Alpaca portfolio, or requesting Alpaca market data sends the user's API key and secret to Alpaca and requests account, asset, fill, fee, or market-data records. eJournal never places, changes, or cancels orders. See [Alpaca's privacy information](https://alpaca.markets/disclosures).
- **TradeZero:** This integration is optional and user-configured. Testing credentials or synchronizing a TradeZero portfolio sends the user's API key and secret to TradeZero and requests account and order-history records. eJournal never places, changes, or cancels orders. See [TradeZero's privacy policy](https://tradezero.com/privacy-policy).
- **Moomoo OpenD:** This integration is optional and connects only to the user-installed OpenD service at `127.0.0.1`. eJournal does not directly send requests to Moomoo's internet services, but OpenD is separate software and may do so under [Moomoo's privacy policy](https://www.moomoo.com/privacy).
- **Links and downloads:** Opening project links or downloading a release uses the user's browser and GitHub. See [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

Responses needed for charts and synchronization are stored in the local journal database. eJournal does not forward information received from one provider to another.

## User choices

Alpaca, TradeZero, and Moomoo synchronization are disabled until the user configures them. Startup broker synchronization is an opt-in setting for each portfolio. Removing stored broker credentials prevents further authenticated requests to that broker.

Yahoo Finance is the default source for daily market data and may be contacted automatically as described above. Users who do not want those requests should not run market-data synchronization and should block eJournal's network access until a dedicated opt-out is available.

## Questions and changes

Privacy questions and reports can be filed through the project's [GitHub issue tracker](https://github.com/earlisreal/eJournal/issues). This policy will be updated when eJournal's storage or network behavior changes.
