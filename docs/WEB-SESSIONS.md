# Sessions web — suivi 1.8.0

Le propriétaire confirme que le correctif de cookies 1.6.2 n’a pas résolu la déconnexion : elle survient après un simple changement de page ou d’application.

## Constats supplémentaires et corrections

1. `stopPanels()` détruisait les deux WebViews à chaque page interne et sortie d’Activity ; `select()` recréait YouTube à chaque changement d’onglet. L’historique sauvegardé par `WebView.saveState()` ne restaure pas le document vivant, sa mémoire JavaScript et son stockage de session. Les panneaux sont maintenant mis en pause et conservés, au plus un Maps et un YouTube. Le retour réutilise les mêmes instances. La destruction de l’Activity libère les deux panneaux.
2. Les liens web ordinaires vers `accounts.google.com` / `accounts.youtube.com` étaient hors des origines autorisées : ils pouvaient partir dans Chrome ou être interrompus si c’était une redirection sans geste. Les parcours de connexion **au site Google lui-même** restent maintenant dans le profil WebView existant, sans changer le User-Agent ou contourner une décision de Google.
3. Une demande d’autorisation OAuth identifiée par ses chemins/paramètres n’est pas exécutée dans WebView. Un écran explicite propose la page complète du site dans un navigateur installé. Un refus HTTP sur le site de compte propose aussi ce parcours. L’ouverture repart de l’accueil du site ; aucun jeton, code ou cookie n’est recopié d’un profil à l’autre. [Politique officielle Google OAuth](https://developers.google.com/identity/protocols/oauth2/policies).
4. Réglages → **Comptes Google · panneaux et applications** explique la séparation des profils et permet d’ouvrir Maps/YouTube dans un navigateur ou leur application installée. Une connexion dans Chrome ou dans YouTube Android ne connecte pas le panneau. Ce parcours externe n’est pas présenté comme une activité embarquée.

La mise en pause demande aussi l’arrêt des éléments HTML audio/vidéo avant `WebView.onPause()` ; elle ne laisse pas volontairement une vidéo continuer pour garder la session. La conservation explicite des cookies et le profil privé de 1.6.2 restent en place. Aucun mot de passe, cookie ou jeton de compte n’est lu, journalisé, exporté ou réinjecté par le code de production.

## Portée et contrôle sur le boîtier

Les essais avec des origines `.example` et des marqueurs synthétiques vérifient le document, son stockage de session, les changements d’onglet/page, la pause d’un média HTML de test et une vraie transition vers l’application Paramètres Android puis retour. Ils ne prouvent pas la validité d’une session Google réelle.

Après mise à jour **sans désinstaller ni effacer les données**, si une reconnexion est nécessaire, faire la connexion depuis le panneau lui-même quand Google l’accepte, puis vérifier Radio → YouTube, Réglages → Retour et sortie vers une autre application → retour. Si la connexion est faite dans le navigateur, y utiliser le site complet : elle ne sera pas transférée au panneau.

Un arrêt forcé du processus, une destruction par Android, un redémarrage du boîtier ou une révocation par Google ne sont pas équivalents à un changement de page : la mémoire du document ne peut pas être promise dans ces cas. Seuls les cookies rendus persistants par le site peuvent être restaurés. Google peut refuser une connexion dans un navigateur intégré ; aucune imitation de Chrome, extraction de comptes Android ou transformation de cookies de session en cookies permanents n’est ajoutée. Si le service refuse le panneau, le navigateur pris en charge ou l’application installée reste la voie disponible. [Navigateurs compatibles avec les comptes Google](https://support.google.com/accounts/answer/7675428?hl=fr).

**La connexion et la déconnexion réelles du compte du propriétaire restent non testées.** Le correctif cible des défauts observables de navigation et de cycle de vie ; il n’est pas présenté comme une garantie d’authentification Google.

---

# Conservation des sessions web — 1.6.2

Le propriétaire signale une déconnexion de son compte dans les panneaux Maps/YouTube après avoir quitté puis rouvert AutoDrive. Il a confirmé que le problème concerne la connexion, pas simplement une adresse e-mail absente de la saisie automatique. Le parcours exact de connexion et la cause sur son appareil restent non reproduits avec un compte Google réel.

## Audit du code 1.6.1

- Les panneaux utilisent le profil WebView privé de l’application. Aucune suppression explicite des cookies, du cache ou du stockage des sites n’a été trouvée dans leur cycle de vie.
- Les WebViews sont détruits puis reconstruits lors de la sortie ou d’un changement d’onglet, pour arrêter les médias et libérer les ressources. Cela ne prouve pas, à soi seul, une suppression des cookies.
- Le stockage DOM était déjà activé, mais les cookies n’étaient pas configurés explicitement. Android désactive par défaut les cookies tiers pour les applications ciblant Android 5 et ultérieur. Aucune sauvegarde explicite `CookieManager.flush()` n’était faite avant la fermeture. Ces deux points sont des facteurs possibles, pas une preuve de la cause de la déconnexion Google signalée.
- Les domaines de connexion Google hors des origines autorisées peuvent être ouverts dans le navigateur externe après une action utilisateur. Ce navigateur a sa propre session ; une connexion externe ne connecte pas automatiquement le panneau AutoDrive.

## Modification

`WebSessionStorage` configure les cookies normaux et les cookies tiers sur les deux panneaux, avant leur navigation. Les cookies tiers restent soumis aux choix de consentement et aux règles du navigateur/service. Le profil WebView existant est réutilisé, sans réinitialisation du compte ou des préférences.

Un enregistrement des cookies sur disque est demandé après le chargement d’une page, la sauvegarde du panneau, sa mise en pause/libération et l’arrêt de l’activité. `flush()` peut faire de l’IO : les demandes sont exécutées sur un dispatcher IO à parallélisme 1, sans référence conservée à l’Activity. L’écriture peut ainsi se terminer après la destruction de la vue, tant que le processus existe. Aucun identifiant, mot de passe, cookie ou jeton n’est lu, journalisé ou exporté par le code de production.

Les dates d’expiration, suppressions et règles des cookies restent celles du service. AutoDrive ne transforme pas des cookies de session en cookies permanents et ne réinjecte pas une sauvegarde après une déconnexion. Le comportement de libération des vues est conservé : cette version ne laisse pas une vidéo YouTube jouer en arrière-plan pour garder un compte connecté.

[API CookieManager : acceptation et flush](https://developer.android.com/reference/android/webkit/CookieManager) · [WebView : sauvegarde de l’historique](https://developer.android.com/reference/android/webkit/WebView#saveState(android.os.Bundle))

## Résultats et limites

Les tests n’utilisent que deux origines réservées `.example`, des cookies synthétiques `Secure; HttpOnly; SameSite=None; Max-Age=3600` et des valeurs sans données personnelles. Ils vérifient la politique des cookies et la conservation des cookies/localStorage après libération puis recréation des deux types de panneaux.

Deux exécutions séparées, avec arrêt forcé du processus entre écriture et lecture, ont retrouvé les cookies persistants des deux origines. L’assertion contrôle aussi que les PID sont différents : 2762 à l’écriture, 2909 à la lecture lors du contrôle. Le stockage local DOM n’a survécu que pour une des deux origines dans cette tentative d’arrêt immédiat. Un premier essai qui exigeait également la durabilité des deux écritures DOM a donc échoué ; le test de frontière de processus vérifie désormais explicitement les cookies et relève séparément le résultat DOM, sans prétendre que celui-ci est garanti.

Il n’existe pas d’API publique WebStorage de vidage immédiat sur disque. Une fermeture forcée peut interrompre une écriture DOM récente. Aucun contournement par extraction et recopie du stockage Google n’a été ajouté. Un arrêt normal avec libération/recréation des panneaux a conservé les données testées.

**Ces tests ne prouvent pas qu’un compte Google réel reste connecté.** Chrome, les applications Maps/YouTube installées et les WebViews n’utilisent pas nécessairement la même session. Google interdit certains parcours OAuth dans les navigateurs intégrés ; AutoDrive ne modifie pas son User-Agent, ne copie pas de cookies du navigateur et ne contourne pas ce refus. Un site peut aussi expirer ou révoquer une session. [Isolation WebView/navigateur](https://developer.chrome.com/docs/android/custom-tabs) · [Règles Google OAuth](https://developers.google.com/identity/protocols/oauth2/policies#secure-browsers).

Après mise à jour, une nouvelle connexion peut être nécessaire si les anciens cookies n’étaient plus présents. Seul un essai avec le parcours réel du propriétaire permettra de confirmer la persistance de son compte. Ne pas effacer les données d’AutoDrive ou le réinstaller en supprimant ses données pendant ce contrôle.
