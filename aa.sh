#for i in {1..10} ; do
for i in {1..11} ; do
#    echo $i >> README.md
#    git add README.md
#    git commit -m "$i"
#    git push origin main
    git reset --soft HEAD~
done