# Common Errors

These page describes common errors with authentication. If the specific advice doesn't help, try either deleting and
adding the account again into the mod or logging in via vanilla launcher.

If this page doesn't help you, feel free to ask for help via [Discord](https://discord.gg/TpU2nEkSPk).

### Unable to connect to Microsoft authentication servers...

Your internet is not working properly or your ISP or region or country is blocking Microsoft and/or Minecraft.
Try checking your internet connection (especially to [microsoft.com](https://microsoft.com) or [minecraft.net](https://minecraft.net))
or try using vanilla launcher to log in first.

### Empty response was received...

You've probably opened previous authentication page or did something similar.
Try fully closing your browser and performing authentication once again.
If this didn't help, try a different browser.

### Access was denied...

You've closed the authentication page. Try again.
If you didn't do this, possibly you're being restricted by your family control.

### Microsoft token exchange failed: this token was issued to a different app/OAuth client...

Refresh tokens are bound to the OAuth client (app) that created them. A token exported from another launcher, alt
generator, or browser session cannot be redeemed by this mod, even if the token itself is still valid. Tokens copied
with **Copy Refresh Token** in this mod or exported from the official Minecraft launcher work, and the mod retries
both OAuth clients automatically. If you still get this error, add the account through **Add → Microsoft** or import
the original cookie file instead.

### Unable to decrypt local account data...

You've entered the wrong decryption password or your hardware has been changed.
Try entering the password again or remove and add the account.

### Your Microsoft account doesn't have an Xbox account linked...

You're probably selecting an account which doesn't own a game.
If that's not the case, try adding this account into the vanilla launcher.

### Xbox Live is not available for this region...

Microsoft and/or Minecraft is not available in this region.
If it should be available, check if you don't have any VPN or proxy enabled.

### Your Xbox account needs to be linked to or verified by an adult...

Current family control settings or Microsoft policies require approval by an adult.
If you don't have any family policies, or you're over 18, check your age in the Microsoft account settings.

### Your Xbox account doesn't have a Minecraft profile...

You're probably selecting an account which doesn't own a game, or you've selected an account with the Xbox Game Pass
which requires setup through vanilla launcher. Anyway, try adding this account into the vanilla launcher.
