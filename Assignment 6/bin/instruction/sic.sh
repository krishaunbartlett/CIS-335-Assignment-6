awk -F ' ' '
BEGIN{ OFS = FS }
{
    if ($1) {
        if (match($4, /[A-F0-9]{2}/)) {
            args = split($2, a, ",")
            if ($3 == "3/4")
                format = "3"
            else
                format = $3
            printf "%s,\t%s,\t%s,\t%s\n", $1, $4, format, args
        }
        else {
            printf "%s,\t%s,\t1,\t1\n", $1, $3
        }
    }
}' $1
