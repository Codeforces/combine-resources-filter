for %%i in (*.js) do 7z a -tgzip %%i.gz %%i

echo %time% > time.z
dir >> time.z
