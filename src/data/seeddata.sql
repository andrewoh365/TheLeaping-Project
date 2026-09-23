
-- Insert Instruments
INSERT INTO instruments (currency_id, instrument_type, tradeable, market, ticker, company_name) VALUES
(1, 'EQUITY', true, 'NASDAQ', 'AAPL', 'Apple Inc.'),
(1, 'EQUITY', true, 'NASDAQ', 'GOOGL', 'Alphabet Inc.'),
(1, 'EQUITY', true, 'NASDAQ', 'MSFT', 'Microsoft Corporation'),
(1, 'EQUITY', true, 'NYSE', 'JPM', 'JPMorgan Chase & Co.'),
(1, 'EQUITY', true, 'NYSE', 'KO', 'The Coca-Cola Company'),
(1, 'ETF', true, 'NASDAQ', 'VOO', 'Vanguard S&P 500 ETF'),
(1, 'ETF', true, 'NASDAQ', 'VTI', 'Vanguard Total Stock Market ETF'),
(1, 'BOND', true, 'NYSE', 'BND', 'Vanguard Total Bond Market ETF'),
(1, 'ETF', true, 'NASDAQ', 'GLD', 'SPDR Gold Shares'),
(1, 'MUTUAL_FUND', true, 'NASDAQ', 'VTSAX', 'Vanguard Total Stock Market Index Fund'),
(1, 'EQUITY', true, 'NYSE', 'TSLA', 'Tesla Inc.'),
(1, 'EQUITY', true, 'NASDAQ', 'AMZN', 'Amazon.com Inc.'),
(1, 'INDEX', false, 'NYSE', 'SPX', 'S&P 500 Index'),
(1, 'INDEX', false, 'NYSE', 'DJI', 'Dow Jones Industrial Average');
