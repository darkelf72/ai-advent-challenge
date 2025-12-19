#!/bin/bash

# Script for stopping MCP servers in Docker containers
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Stopping HTTP MCP Server${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Get the project root directory (2 levels up from scripts directory)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed!${NC}"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    exit 1
fi

# Check if container is running
HTTP_RUNNING=$(docker ps -q --filter "name=http-mcp-server")

if [ -z "$HTTP_RUNNING" ]; then
    echo -e "${YELLOW}HTTP MCP server is not running.${NC}"
    exit 0
fi

# Stop the container
echo -e "${GREEN}Stopping http-mcp-server...${NC}"
docker stop http-mcp-server 2>/dev/null

# Check if container stopped successfully
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}HTTP MCP Server stopped successfully!${NC}"
    echo ""
    exit 0
else
    echo -e "${RED}Failed to stop HTTP MCP server!${NC}"
    exit 1
fi
