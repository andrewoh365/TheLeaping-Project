// Things that happen on the dashboard which the walkthrough waits for.
// The dashboard reports them through DashboardComponent.onTourEvent().
export type TourEvent =
  | 'cashPanelOpened'
  | 'cashPanelClosed'
  | 'depositCompleted'
  | 'tradePanelOpened'
  | 'tradePanelClosed'
  | 'orderPlaced';

export interface WalkthroughStep {
  id: string;
  // Matches a data-tour="..." attribute in the dashboard template.
  // null shows the popup in the middle of a fully dimmed screen.
  target: string | null;
  title: string;
  body: string;
  // Shown instead of body when the US stock market is closed.
  bodyWhenMarketClosed?: string;
  // The step finishes when this event happens instead of on a Next button,
  // and the highlighted area can be clicked.
  advanceOn?: TourEvent;
  // Go back one step when this happens, e.g. the user closed a panel mid-step.
  backOn?: TourEvent;
}

export const WALKTHROUGH_STEPS: WalkthroughStep[] = [
  {
    id: 'welcome',
    target: null,
    title: 'Welcome to Leaping!',
    body: 'Would you like a quick tour? We\'ll help you add cash, place your first trade and show you around your dashboard. It takes about two minutes.'
  },
  {
    id: 'transfer-button',
    target: 'transfer-button',
    title: 'Add some cash',
    body: 'You need money in your account before you can invest. Click Transfer to make a deposit.',
    advanceOn: 'cashPanelOpened'
  },
  {
    id: 'transfer-panel',
    target: 'cash-panel',
    title: 'Make a deposit',
    body: 'Pick one of the quick amounts, such as $10,000, or type your own. Then press the Deposit button.',
    advanceOn: 'depositCompleted',
    backOn: 'cashPanelClosed'
  },
  {
    id: 'trade-button',
    target: 'trade-button',
    title: 'Time to invest',
    body: 'Your cash is in. Now click Trade to buy your first investment.',
    advanceOn: 'tradePanelOpened'
  },
  {
    id: 'trade-panel',
    target: 'trade-panel',
    title: 'Place your first trade',
    body: 'Search for a company such as Apple (AAPL), choose it, enter how many shares you want, then press Buy.',
    bodyWhenMarketClosed: 'The US stock market is closed right now, but crypto trades 24/7. Search for Bitcoin (BTC), choose it, enter a small quantity such as 0.01, then press Buy.',
    advanceOn: 'orderPlaced',
    backOn: 'tradePanelClosed'
  },
  {
    id: 'close-trade-panel',
    target: 'trade-panel-close',
    title: 'Nice work!',
    body: 'Your order went through. Close the trade panel to head back to your dashboard.',
    advanceOn: 'tradePanelClosed'
  },
  {
    id: 'performance-chart',
    target: 'performance-chart',
    title: 'Portfolio Performance',
    body: 'This chart tracks the value of your investments plus cash over time. Use the 1D to ALL buttons to change the period. It fills in as your account builds up history.'
  },
  {
    id: 'holdings',
    target: 'holdings',
    title: 'Your holdings',
    body: 'Everything you own is listed here, with what you paid, today\'s price and your gain or loss. The tabs switch to your Watchlist, Crypto and a history of your orders under Activity.'
  },
  {
    id: 'market-news',
    target: 'market-news',
    title: 'Market News',
    body: 'The latest market headlines. Click any story to read it in a new tab.'
  },
  {
    id: 'market-movers',
    target: 'market-movers',
    title: 'Market Movers',
    body: 'Today\'s biggest gainers and losers. Click one at any time to open it in the trade panel. That\'s the tour. Happy investing!'
  }
];
