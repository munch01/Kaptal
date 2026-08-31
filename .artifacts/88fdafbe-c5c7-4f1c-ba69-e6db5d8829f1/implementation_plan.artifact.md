# Fix Crypto Balance and Display Issues

The goal is to fix the issue where crypto balances (both quantity and EUR value) are not showing up correctly on the Home Screen and the Account Detail page.

## User Review Required

> [!IMPORTANT]
> I will be adding the display of the crypto quantity (e.g., "0.5 BTC") to the header of the Crypto Detail screen. This will help you verify that the quantity is correctly loaded even if the market value (EUR) is still loading from the API.

## Proposed Changes

### ViewModel & Logic

#### [MODIFY] [MainViewModel.kt](file:///C:/Users/emeri/AndroidStudioProjects/Kaptal/composeApp/src/commonMain/kotlin/com/Muncho/kaptal/viewmodel/MainViewModel.kt)
- Improve `fetchCryptoRates` to handle potential API failures and ensure rates are updated.
- Refine `calculateCurrentRealBalance` for crypto to ensure it's as direct as possible.
- Ensure `calculateTotalInvestment` handles missing history prices more gracefully by defaulting to the current rate if history is not yet available.

### UI Components

#### [MODIFY] [CryptoScreen.kt](file:///C:/Users/emeri/AndroidStudioProjects/Kaptal/composeApp/src/commonMain/kotlin/com/Muncho/kaptal/screens/CryptoScreen.kt)
- Add the crypto quantity display (e.g., "0.5 BTC") to the main header card.
- Use the current rate as a fallback for `referencePrice` if history is still loading.
- Add a loading state or placeholder when rates are 0.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/emeri/AndroidStudioProjects/Kaptal/composeApp/src/commonMain/kotlin/com/Muncho/kaptal/screens/HomeScreen.kt)
- Add a small check to avoid showing "≈ 0.00 €" if the rate hasn't been fetched yet (show nothing or a placeholder).

## Verification Plan

### Manual Verification
1. Open a Crypto account.
2. Check that the quantity (e.g., "0.5 BTC") appears in the header.
3. Verify that the EUR value updates once the rates are fetched.
4. Check the Home Screen to see if the quantity and EUR value are correctly displayed.
