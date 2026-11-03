IN ILLUSIO - Pack d'icone Android
================================

Les fichiers app/src/main/res/ sont des ressources a copier dans le projet Android.

1. Copiez le contenu de ce dossier "app/src/main/res/" dans le dossier identique de votre projet.
2. Dans app/src/main/AndroidManifest.xml, ajoutez sur la balise <application> :
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher_round"
3. Compilez avec Gradle 8.13 et verifiez l'icone sur un appareil.
4. "play-store/InIllusio_Play_512.png" est l'icone carree 512x512 a charger sur Google Play.

Une version adaptive avec premier plan transparent, fond blanc casse et icone thematique
Android 13+ est incluse. N'utilisez pas l'icone Play Store comme ressource de premier plan
pour l'icone adaptive : elle contient deja un fond.

Ne copiez pas le dossier play-store/ dans les assets de l'application.
