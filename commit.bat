@echo off
set /p commitMsg=Enter commit message: 
git add -A
git commit -m "%commitMsg%"
git push -f origin master
pause
