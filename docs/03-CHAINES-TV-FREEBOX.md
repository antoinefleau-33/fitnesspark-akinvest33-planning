# 📺 Les chaînes TV de la Freebox sur FreePlay OS

Free permet officiellement de regarder les chaînes TV de l'abonnement Freebox
sur les appareils du **réseau local** : la Freebox publie une liste de chaînes
(playlist) à l'adresse :

```
http://mafreebox.freebox.fr/freeboxtv/playlist.m3u
```

FreePlay OS exploite ça de deux façons, déjà préinstallées.

## Option A — Kodi : la vraie expérience "box TV" (recommandé)

Kodi affiche les chaînes avec zapping, guide des programmes et télécommande
virtuelle. La playlist Freebox est **déjà configurée** ; il reste une
activation à faire à la première ouverture :

1. Ouvre **Kodi**.
2. Va dans **Extensions → Mes extensions → Clients PVR → PVR IPTV Simple
   Client → Activer**.
3. Retourne à l'accueil : un menu **TV** est apparu, avec les chaînes Freebox.

Astuce télé : dans Kodi, Paramètres → Interface → Skin, le thème par défaut se
pilote très bien à la manette ou avec un mini-clavier sans fil.

## Option B — OQEE by Free : partout, y compris hors de chez toi

**OQEE** est l'application TV officielle de Free (chaînes, replay, guide,
enregistrements). L'appli « OQEE by Free » sur le bureau ouvre
<https://oqee.tv> en plein écran — connexion avec tes identifiants Free.

C'est la solution pour les **chaînes cryptées** absentes de la playlist locale
et pour regarder la TV **en dehors du domicile**.

## Option C — VLC, pour dépanner

VLC lit directement la playlist : **Média → Ouvrir un flux réseau** → colle
`http://mafreebox.freebox.fr/freeboxtv/playlist.m3u`. Pratique pour vérifier
que le flux TV fonctionne.

## Dépannage

| Problème | Cause probable | Solution |
|---|---|---|
| Aucune chaîne ne se charge | Le PC n'est pas sur le réseau de la Freebox | Connecte la machine (câble ou Wi-Fi) à la Freebox ; ça ne marche pas depuis un autre réseau |
| « mafreebox.freebox.fr introuvable » | DNS/réseau | Vérifie que la Freebox est bien le routeur du réseau |
| Certaines chaînes affichent une erreur | Chaîne cryptée ou hors abonnement | Utilise OQEE pour ces chaînes |
| Ça coupe/saccade en Wi-Fi | Signal faible | Rapproche la machine, passe en 5 GHz, ou branche un câble Ethernet |
| L'option TV n'apparaît pas dans Kodi | Extension PVR pas activée | Refais l'étape d'activation de l'option A |

## Note

La playlist locale est une fonction fournie par la Freebox elle-même : si Free
la fait évoluer un jour, OQEE (option B) reste la voie officielle et pérenne.
