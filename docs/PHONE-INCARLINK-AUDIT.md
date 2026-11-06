# Téléphone Bluetooth Incarlink — audit du 6 novembre 2026

Le problème signalé concerne l’Incarlink. Les rapports Android 17 du Samsung ne prouvent aucune capacité Bluetooth du boîtier.

## Causes identifiées dans AutoDrive 1.6.2

- Le champ du numéro désactivait explicitement le clavier système. Le pavé ne restaurait pas explicitement le curseur après insertion ; une sélection absente était traitée comme la position zéro.
- Aucun carnet n’était lu. Les six favoris étaient des entrées manuelles, sans synchronisation Bluetooth.
- Sans association choisie, `ACTION_DIAL` résolvait le composeur Android générique. Sur un boîtier, celui-ci ne constitue pas une liaison avec le téléphone Bluetooth et peut concerner sa propre téléphonie/SIM.
- L’échec de réception du numéro par une application constructeur produisait seulement un message. Ouvrir cette application et lui commander un appel sont deux fonctions distinctes.

## Identification constructeur en lecture seule

Source primaire : [page Carlinkit SLM927](https://www.carlinkit.com/download/slm927.html), archive `update_x15_slm927_A13_chelianyi-led-2025-05-29-17-05.zip` déjà utilisée pour l’audit de l’interface. Aucune image n’a été flashée, aucun APK constructeur installé ou modifié.

Le manifeste de `system/app/SdBluetooth/SdBluetooth.apk` identifie :

| Élément | Valeur observée |
| --- | --- |
| Nom | BT Phone |
| Paquet | `com.carsyso.bluetooth` |
| Version | 2.0.8 / code 48 |
| Activité publique MAIN/LAUNCHER | `com.carsyso.bluetoothcall.MainActivity` |
| UID partagé déclaré | `android.uid.system` |
| Réception standard ACTION_DIAL / ACTION_CALL / tel | Aucune déclarée dans ce manifeste |
| Fournisseur de contacts exporté | Aucun déclaré dans ce manifeste |
| SHA-256 de l’APK inspecté | `d02a991076e8fad093b88e6a44d201388d91e4a9a70e0917b7559d6082a66f28` |

L’image est de mai 2025 ; la photo du propriétaire indique août 2025. **La présence et le comportement de cette version précise sur son boîtier restent à confirmer.** AutoDrive vérifie à l’exécution que l’activité est installée, activée, exportée et accessible. Il respecte toujours une association explicitement choisie ; une application choisie puis absente ne provoque aucun basculement silencieux.

Le constructeur décrit l’utilisation de l’application [BT Phone pour la liaison du téléphone sur les Tbox](https://www.carlinkit.com/faq_carlinkit_not_connecting.html). Cela ne documente pas une API tierce de synchronisation ou de numérotation. Les services exportés présents dans l’image ne constituent pas à eux seuls un contrat public utilisable. La recherche n’a identifié aucun SDK constructeur publié définissant les commandes, permissions et compatibilités nécessaires. AutoDrive ne devine pas de transactions Binder, commandes série, broadcasts privés ou paramètres d’intents.

## Comportement 1.7.0

1. **Phone** ouvre le clavier amélioré, les contacts et les favoris. L’appui sur le champ permet le clavier Android ; le pavé conserve le curseur et remplace la sélection correctement. `0` prolongé saisit `+` ; effacement prolongé vide le champ.
2. En mode **Automatique**, BT Phone est sélectionné s’il est détecté. S’il est absent, AutoDrive demande de choisir une application Bluetooth et ne bascule pas automatiquement vers un composeur SIM. Réglages → Application Téléphone permet de choisir une autre application ou de rétablir ce mode. Le bouton en haut du panneau ouvre son interface réelle pour la connexion, les contacts privés et les appels.
3. **Contacts** demande `READ_CONTACTS` uniquement à l’appui sur Autoriser les contacts. Lecture du fournisseur Android standard hors du thread UI, recherche par nom ou numéro, plusieurs numéros par contact, dédoublonnage et actualisation sur notification du fournisseur. Au plus 5 000 lignes normalisées sont chargées et cette limite est signalée si atteinte. Les contacts ne sont pas enregistrés, journalisés ou envoyés sur Internet. La lecture et l’observation s’arrêtent quand la page n’est plus active.
4. Un contact sélectionné remplit le champ ; il ne déclenche pas d’appel. Les favoris manuels existants restent disponibles. Si le carnet est vide, refusé ou inaccessible, le panneau le dit et conserve l’accès à l’application téléphone.
5. Si l’application choisie annonce une réception standard `ACTION_DIAL`, **Préparer l’appel** lui transmet le numéro. Elle confirme et gère l’appel. Si elle ne la propose pas, **Copier et ouvrir** copie explicitement le numéro (presse-papiers marqué sensible), puis ouvre son écran. Il faut alors coller le numéro dans cette application. **Ce n’est pas un appel Bluetooth automatique.** Aucun `CALL_PHONE`, rôle composeur ou appel SIM implicite n’est ajouté.

## Ce que le logiciel ne peut pas garantir sans le boîtier

`READ_CONTACTS` donne accès aux contacts du fournisseur local Android ; il ne crée pas une synchronisation PBAP avec le mobile. Si BT Phone garde son carnet dans une base privée, la liste AutoDrive restera vide même si ses contacts apparaissent dans BT Phone. Il faut alors utiliser le carnet de BT Phone ou obtenir une interface prise en charge par son fabricant.

Les profils clients HFP/PBAP décrits par [AOSP Automotive](https://source.android.com/docs/automotive/ivi_connectivity) sont des fonctions système et n’établissent pas leur disponibilité pour une application ordinaire sur cette AI box. La connexion Bluetooth Android générique ou un appareil simplement associé ne prouve pas une liaison d’appels active. AutoDrive n’affiche donc pas de téléphone « connecté » inventé. [Accès officiel au fournisseur Contacts](https://developer.android.com/identity/providers/contacts-provider/retrieve-names), [intents téléphoniques](https://developer.android.com/guide/components/intents-common#Phone).

Les essais de saisie, contacts Android et résolution d’application sont séparés des points suivants, **non testés sur le matériel propriétaire** : connexion au Samsung, import PBAP, présence de contacts constructeur dans le fournisseur Android, collage dans BT Phone, numérotation réelle, appels entrants, micro, audio, reprise de la radio et commandes au volant. Aucun appel réel n’est passé dans les essais.
