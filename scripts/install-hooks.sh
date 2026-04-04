#!/bin/bash

cd "$(dirname "$0")/.."

# 备份现有 hooks
if [ -f .git/hooks/pre-commit ]; then
    cp .git/hooks/pre-commit .git/hooks/pre-commit.backup
fi

# 安装 pre-commit hook
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

echo "Git hooks installed successfully!"
echo ""
echo "Installed hooks:"
echo "  - pre-commit: Runs quick tests before commit"
echo ""
echo "To bypass pre-commit hook (not recommended):"
echo "  git commit --no-verify"
