# Spécification de Conception : Dossier Dynamique lors du Changement de Territoire (`capture_territoires.js`)

**Date :** 2026-10-05  
**Auteur :** Assistant IA & samue  
**Statut :** Validé  
**Portée :** Script Node.js / Playwright `maps/capture_territoires.js`

---

## 1. Contexte & Problématique

Lors de l'exécution du script `capture_territoires.js`, l'outil itère séquentiellement sur la liste des territoires disponibles dans la page (`#territories`). Pour chaque territoire, une pause interactive (`waitForUserOnScreenValidation`) affiche un bandeau flottant permettant à l'utilisateur d'ajuster le zoom et le cadrage avant de déclencher les 5 captures d'écran et la sauvegarde des métadonnées JSON.

Cependant, le dossier de sauvegarde était calculé en début d'itération à partir de la valeur sélectionnée programmatiquement (`options[i]`). Si l'utilisateur sélectionnait manuellement un autre territoire dans la liste déroulante de l'interface Google Maps pendant la pause interactive, les captures étaient enregistrées dans le dossier du territoire d'origine plutôt que dans celui du territoire effectivement affiché à l'écran.

---

## 2. Objectifs & Exigences

1. **Mise à jour visuelle en direct :**
   - Lorsqu'un utilisateur sélectionne un autre territoire dans le menu déroulant `#territories`, le bandeau flottant Playwright (`#pw-floating-bar`) doit afficher immédiatement le nouveau nom du territoire.
2. **Sauvegarde dynamique :**
   - Lors du clic sur *"📸 Lancer les 5 captures"*, le dossier de destination doit être déterminé dynamiquement d'après le territoire sélectionné dans l'interface au moment du clic.
   - Le dossier cible doit être créé automatiquement s'il n'existe pas.
3. **Poursuite de la boucle :**
   - La boucle d'itération poursuit son ordonnancement régulier après la capture du territoire sélectionné (comportement "Dossier dynamique seul" validé par l'utilisateur).
4. **Cohérence des métadonnées :**
   - Les métadonnées JSON générées par `takeFullMapScreenshot` correspondront fidèlement au territoire actif au moment de la prise de vue.

---

## 3. Architecture & Conception Technique

### 3.1. Fonction utilitaire de nettoyage du nom de dossier (`getTerritoryDir`)

Une fonction dédiée est introduite pour centraliser la génération du chemin de dossier sécurisé :

```javascript
function getTerritoryDir(territoryText) {
    let cleanName = territoryText.replace(/[/\\?%*:|"<>]/g, '_');
    cleanName = cleanName.replace(/[/n°]/g, '');
    return path.join(OUTPUT_DIR, cleanName.trim());
}
```

### 3.2. Écoute dynamique dans `waitForUserOnScreenValidation`

Dans `page.evaluate` :
1. Un identifiant DOM `pw-territory-title` est attribué au conteneur de texte du territoire dans le bandeau flottant.
2. Un écouteur sur l'événement `change` (et `input`) est attaché au sélecteur `#territories` du DOM.
3. Dès déclenchement, l'intitulé du bandeau est mis à jour :
   ```javascript
   const select = document.getElementById('territories');
   const updateTitle = (text) => {
       const titleEl = document.getElementById('pw-territory-title');
       if (titleEl) {
           titleEl.textContent = `📍 [${index}/${total}] ${text}`;
       }
   };
   select?.addEventListener('change', () => {
       const currentText = select.options[select.selectedIndex]?.text?.trim() || name;
       updateTitle(currentText);
   });
   ```
4. Lors du clic sur le bouton de capture :
   ```javascript
   btnCapture.onclick = () => {
       btnCapture.innerText = '⏳ Captures en cours...';
       btnCapture.style.background = '#e0a800';
       const selectedText = select?.options[select.selectedIndex]?.text?.trim() || name;
       const selectedValue = select?.value || '';
       window.__pw_action = {
           type: 'capture',
           territoryText: selectedText,
           territoryValue: selectedValue
       };
   };
   ```
5. Nettoyage de l'écouteur d'événement à la sortie pour éviter toute accumulation d'écouteurs entre itérations.

### 3.3. Adaptation de la boucle d'exécution Node.js

1. La boucle attend le retour de `waitForUserOnScreenValidation(...)`.
2. Si `action.type === 'stop'`, arrêt de la boucle (`break`).
3. Si `action.type === 'skip'`, passage au territoire suivant (`continue`).
4. Si `action.type === 'capture'` :
   - `effectiveName = action.territoryText || territory.text;`
   - `effectiveDir = getTerritoryDir(effectiveName);`
   - Création récursive du répertoire si nécessaire : `fs.mkdirSync(effectiveDir, {recursive: true});`
   - Log console : `📂 Dossier de sauvegarde : ${effectiveDir}`
   - Exécution des 5 captures et sauvegarde du JSON dans `effectiveDir`.

---

## 4. Stratégie de Validation & Tests

- **Vérification syntaxique :** Exécution de `node -c capture_territoires.js` pour s'assurer de l'absence d'erreurs de syntaxe.
- **Contrôle de non-régression :** Vérification que la capture sans changement manuel continue de sauvegarder dans le dossier nominal du territoire.
