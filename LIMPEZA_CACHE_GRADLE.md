# Instruções para Limpeza do Cache do Gradle

## 🚨 PROBLEMA IDENTIFICADO

O erro `android.enableBuildCache` pode persistir devido a cache antigo do Gradle ou uso de versão anterior do projeto.

## 🔧 SOLUÇÃO COMPLETA

### **Passo 1: Verificar Versão do Projeto**
Certifique-se de estar usando a versão mais recente:
- **Arquivo correto:** `Videolaringoscopio_PROJETO_BUILDCACHE_CORRIGIDO_FINAL.zip`
- **NÃO usar:** Versões anteriores que ainda contêm a configuração depreciada

### **Passo 2: Limpar Cache do Gradle**
Execute os comandos no Terminal (na pasta do projeto):

```bash
# 1. Parar daemon do Gradle
./gradlew --stop

# 2. Limpar projeto
./gradlew clean

# 3. Limpar cache do Gradle (macOS)
rm -rf ~/.gradle/caches/
rm -rf ~/.gradle/daemon/

# 4. Limpar cache do Android Studio (macOS)
rm -rf ~/Library/Caches/AndroidStudio*
rm -rf ~/Library/Application\ Support/AndroidStudio*/caches/

# 5. Limpar cache local do projeto
rm -rf .gradle/
rm -rf app/build/
rm -rf build/
```

### **Passo 3: Invalidar Cache do Android Studio**
1. Abra o Android Studio
2. Vá em **File** > **Invalidate Caches and Restart**
3. Clique em **Invalidate and Restart**

### **Passo 4: Reabrir o Projeto**
1. Feche completamente o Android Studio
2. Reabra o Android Studio
3. Abra o projeto corrigido
4. Aguarde a sincronização do Gradle

### **Passo 5: Compilar**
```bash
# Compilar projeto limpo
./gradlew assembleDebug
```

## 🎯 VERIFICAÇÃO FINAL

### **Confirmar que NÃO há a configuração depreciada:**
```bash
# Procurar por android.enableBuildCache
grep -r "android.enableBuildCache" . --include="*.gradle" --include="*.properties"

# Resultado esperado: Nenhuma linha ativa (apenas comentários ou nada)
```

### **Arquivos que DEVEM estar limpos:**
- ✅ `gradle.properties` - SEM android.enableBuildCache
- ✅ `gradle/gradle.properties` - SEM android.enableBuildCache  
- ✅ `app/build.gradle` - SEM android.enableBuildCache
- ✅ `build.gradle` - SEM android.enableBuildCache

## 🚀 RESULTADO ESPERADO

Após seguir todos os passos:
- ✅ Gradle sincroniza sem erros
- ✅ Build bem-sucedido
- ✅ Sem mensagens de configuração depreciada
- ✅ Projeto funciona normalmente

## 🔍 DIAGNÓSTICO DE PROBLEMAS

### **Se o erro persistir:**

1. **Verificar versão do Android Gradle Plugin:**
   ```gradle
   // Em build.gradle (Project)
   classpath 'com.android.tools.build:gradle:8.1.0' // ou superior
   ```

2. **Verificar versão do Gradle Wrapper:**
   ```properties
   // Em gradle/wrapper/gradle-wrapper.properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip
   ```

3. **Verificar se há configurações globais:**
   ```bash
   # Verificar gradle.properties global (macOS)
   cat ~/.gradle/gradle.properties
   ```

## 📞 SUPORTE ADICIONAL

Se o problema persistir após todos os passos:
1. Envie o log completo do erro
2. Confirme qual versão do projeto está sendo usada
3. Verifique se todos os caches foram limpos corretamente

---
**Status:** Instruções para resolução definitiva do erro android.enableBuildCache

