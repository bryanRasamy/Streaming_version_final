#!/bin/bash

# ========================================
# Script de compilation et exécution
# Projet : Streaming TCP
# ========================================

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Dossiers
CLASSES_DIR="./classe"

# Classe principale à lancer
MAIN_CLASS="view.Main"

# ========================================
# 1. NETTOYAGE (optionnel)
# ========================================
echo -e "${YELLOW}🧹 Nettoyage des anciennes classes...${NC}"
if [ -d "$CLASSES_DIR" ]; then
    rm -rf "$CLASSES_DIR"
fi
mkdir -p "$CLASSES_DIR"

# ========================================
# 2. COMPILATION
# ========================================
echo -e "${YELLOW}🔨 Compilation du code...${NC}"

# Trouver tous les fichiers .java et les compiler
find -name '*.java' -type f | xargs javac -d "$CLASSES_DIR" -encoding UTF-8

# Vérifier si la compilation a réussi
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Compilation échouée !${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Compilation réussie !${NC}"

# ========================================
# 3. CONFIGURATION X11 (pour Linux)
# ========================================
echo -e "${YELLOW}🖥️  Configuration de l'affichage...${NC}"

# Vérifier si DISPLAY est déjà défini
if [ -z "$DISPLAY" ]; then
    export DISPLAY=:1
    echo -e "${YELLOW}   DISPLAY défini à :1${NC}"
else
    echo -e "${GREEN}   DISPLAY déjà défini : $DISPLAY${NC}"
fi

# Autoriser l'accès à X11
xhost +local: > /dev/null 2>&1

# ========================================
# 4. EXÉCUTION
# ========================================
echo -e "${YELLOW}🚀 Lancement du programme...${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

java -Djava.awt.headless=false -cp "$CLASSES_DIR" $MAIN_CLASS

# Vérifier si l'exécution a réussi
if [ $? -ne 0 ]; then
    echo ""
    echo -e "${RED}❌ Exécution échouée !${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ Programme terminé avec succès !${NC}"
