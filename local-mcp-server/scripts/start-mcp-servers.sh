#!/bin/bash

# Script for starting MCP servers in Docker containers
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Starting HTTP MCP Server${NC}"
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

# Start only HTTP MCP server
echo -e "${GREEN}Starting http-mcp-server...${NC}"
docker-compose up -d http-mcp-server

# Check if container started successfully
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}HTTP MCP Server started successfully!${NC}"
    echo ""
    echo -e "${GREEN}Running container:${NC}"
    docker ps --filter "name=http-mcp-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    echo -e "${GREEN}Access URL:${NC}"
    echo -e "  - HTTP MCP Server:  http://localhost:8082"
    echo ""
    exit 0
else
    echo -e "${RED}Failed to start HTTP MCP server!${NC}"
    exit 1
fi
