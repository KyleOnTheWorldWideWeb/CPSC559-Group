@echo off
echo Creating project directories...

mkdir docker\chatserver
mkdir docker\addressingserver
mkdir docker\client
mkdir docker\database

mkdir src\io\github\cpsc559\team16\chatserver
mkdir src\io\github\cpsc559\team16\addressingserver
mkdir src\io\github\cpsc559\team16\client
mkdir src\io\github\cpsc559\team16\database

mkdir tests\chatserver
mkdir tests\addressingserver
mkdir tests\client

mkdir config
mkdir scripts
mkdir data

echo Creating initial files...
echo. > docker\chatserver\Dockerfile
echo. > docker\addressingserver\Dockerfile
echo. > docker\client\Dockerfile
echo. > docker\database\docker-compose.yml

echo. > src\io\github\cpsc559\team16\chatserver\ChatServer.java
echo. > src\io\github\cpsc559\team16\addressingserver\AddressingServer.java
echo. > src\io\github\cpsc559\team16\client\Client.java
echo. > src\io\github\cpsc559\team16\database\DatabaseConnector.java

echo. > tests\chatserver\TestChatServer.java
echo. > tests\addressingserver\TestAddressingServer.java
echo. > tests\client\TestClient.java

echo. > config\application.properties
echo. > scripts\deploy.sh
echo. > scripts\run_tests.sh


echo Done
