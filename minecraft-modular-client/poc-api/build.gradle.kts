plugins { java }
// Aucune dépendance : ce jar est chargé par le classloader racine et partagé avec toutes les
// sessions de jeu. Y ajouter une librairie l'exposerait à Minecraft et créerait des collisions.
