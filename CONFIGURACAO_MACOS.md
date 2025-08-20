# Configuração do Projeto Android para macOS

## 🚀 Guia de Configuração Completo

### **Pré-requisitos**
1. **Android Studio** instalado
2. **JDK 17** instalado
3. **Android SDK** configurado

### **Passo 1: Descobrir o Caminho Correto do JDK**

#### **Método 1: Comando java_home (Recomendado)**
```bash
# Descobrir caminho do JDK 17
/usr/libexec/java_home -v 17

# Listar todos os JDKs instalados
/usr/libexec/java_home -V
```

#### **Método 2: Verificar instalações comuns**
```bash
# Homebrew (Intel Mac)
ls /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Homebrew (Apple Silicon Mac)
ls /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Oracle JDK
ls /Library/Java/JavaVirtualMachines/*/Contents/Home

# Verificar versão atual
java -version
```

### **Passo 2: Configurar JDK no Projeto**

#### **Opção A: Detecção Automática (Recomendado)**
O projeto está configurado para detectar automaticamente o JDK. Não faça nada se o JDK 17 estiver configurado como padrão.

#### **Opção B: Configuração Manual**
1. Execute o comando para descobrir o caminho:
   ```bash
   /usr/libexec/java_home -v 17
   ```

2. Copie o resultado (exemplo: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`)

3. Edite `gradle.properties` e descomente a linha correspondente:
   ```properties
   # Descomente e ajuste com SEU caminho:
   org.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ```

### **Passo 3: Configurar Android SDK**
1. Abra o Android Studio
2. Vá em **Preferences** > **Appearance & Behavior** > **System Settings** > **Android SDK**
3. Copie o caminho mostrado em "Android SDK Location"
4. Edite o arquivo `local.properties` e descomente a linha:
   ```properties
   sdk.dir=/Users/$USER/Library/Android/sdk
   ```
5. Cole o caminho correto do seu SDK

### **Passo 4: Resolver Erros Específicos**

#### **Erro: "Java home supplied is invalid"**
**Solução:**
1. Execute: `/usr/libexec/java_home -v 17`
2. Copie o resultado
3. Edite `gradle.properties`:
   ```properties
   org.gradle.java.home=CAMINHO_COPIADO_AQUI
   ```

#### **Erro: "Invalid Gradle JDK configuration"**
**Solução:**
1. No Android Studio: **File** > **Project Structure**
2. Em **SDK Location** > **Gradle Settings**
3. Configure **Gradle JDK** para usar JDK 17

#### **Erro: "Error loading project"**
**Solução:**
1. Feche o Android Studio
2. Delete a pasta `.idea` (se existir)
3. Reabra o projeto no Android Studio
4. Quando perguntado sobre "Trust Project", clique em **Trust**

### **Passo 5: Instalação do JDK (se necessário)**

#### **Método 1: Homebrew (Recomendado)**
```bash
# Instalar Homebrew (se não tiver)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Instalar OpenJDK 17
brew install openjdk@17

# Criar link simbólico
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

#### **Método 2: Download Direto**
1. Baixe JDK 17 em: https://adoptium.net/temurin/releases/
2. Instale o arquivo `.pkg`
3. Verifique: `java -version`

### **Passo 6: Compilar o Projeto**
```bash
# Limpar projeto
./gradlew clean

# Compilar
./gradlew assembleDebug
```

### **Configurações Finais Testadas**

#### **gradle.properties (Configuração Flexível):**
```properties
# JDK será detectado automaticamente
# Para configurar manualmente, descomente com SEU caminho:
# org.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

org.gradle.jvmargs=-Xmx2048M -XX:MaxMetaspaceSize=512M
org.gradle.parallel=true
org.gradle.caching=true
```

#### **local.properties:**
```properties
# Ajuste com SEU caminho do Android SDK:
sdk.dir=/Users/$USER/Library/Android/sdk
```

### **Verificação Final**
1. ✅ `java -version` mostra JDK 17
2. ✅ `/usr/libexec/java_home -v 17` retorna caminho válido
3. ✅ Projeto carrega sem erros no Android Studio
4. ✅ `./gradlew clean` executa sem erros
5. ✅ `./gradlew assembleDebug` compila com sucesso

### **Comandos de Diagnóstico**
```bash
# Verificar JDK instalado
java -version
javac -version

# Descobrir caminho do JDK
/usr/libexec/java_home -v 17

# Listar todos os JDKs
/usr/libexec/java_home -V

# Verificar Android SDK
ls ~/Library/Android/sdk

# Testar Gradle
./gradlew --version
```

## 🎯 Status
**CONFIGURADO CORRETAMENTE** - Projeto pronto para desenvolvimento no macOS!

