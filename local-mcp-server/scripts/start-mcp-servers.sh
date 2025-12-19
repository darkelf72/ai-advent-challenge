#!/bin/bash

# Script for starting MCP servers in Docker containers
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Starting MCP Servers${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Get the project root directory (2 levels up from scripts directory)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed!${NC}"
    echo "Please install Docker Desktop from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    echo "Please start Docker Desktop and try again."
    exit 1
fi

echo -e "${GREEN}Docker is installed and running.${NC}"
echo ""

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${RED}Error: docker-compose.yml not found in project root!${NC}"
    exit 1
fi

# Start only MCP servers (db-mcp-server and http-mcp-server)
echo -e "${GREEN}Starting db-mcp-server and http-mcp-server...${NC}"
docker-compose up -d db-mcp-server http-mcp-server

# Check if containers started successfully
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}MCP Servers started successfully!${NC}"
    echo ""
    echo -e "${GREEN}Running containers:${NC}"
    docker ps --filter "name=db-mcp-server" --filter "name=http-mcp-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    echo -e "${GREEN}Access URLs:${NC}"
    echo -e "  - DB MCP Server:    http://localhost:8081"
    echo -e "  - HTTP MCP Server:  http://localhost:8082"
    echo ""
    exit 0
else
    echo -e "${RED}Failed to start MCP servers!${NC}"
    exit 1
fi
