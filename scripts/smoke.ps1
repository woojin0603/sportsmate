$ErrorActionPreference='Stop'
$root='http://localhost:8080'
if ([string]::IsNullOrWhiteSpace($env:IMPORT_KEY)) { throw 'Set IMPORT_KEY to the same value used by the server.' }
function Assert([bool]$condition,[string]$message) { if (-not $condition) { throw $message } }
function ExpectStatus([int]$expected,[scriptblock]$call,[string]$message) {
    try { & $call | Out-Null; throw "$message accepted unexpectedly" }
    catch { if ([int]$_.Exception.Response.StatusCode -ne $expected) { throw "$message returned unexpected status" } }
}
$browser=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf=Invoke-RestMethod "$root/api/csrf" -WebSession $browser
$csrfHeader=@{'X-XSRF-TOKEN'=$csrf.token}
$username='test'+[guid]::NewGuid().ToString('N').Substring(0,10)
$signup=@{fullName='홍길동';username=$username;password='ExamplePassword123!';birthDate='1998-05-10';email="$username@example.test";phoneNumber='010-1234-5678';gender='MALE'}|ConvertTo-Json
ExpectStatus 403 { Invoke-RestMethod "$root/api/users/signup" -Method Post -WebSession $browser -ContentType 'application/json' -Body $signup } 'missing CSRF'
$member=Invoke-RestMethod "$root/api/users/signup" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body $signup
Assert ($member.username -eq $username) 'signup mismatch'
ExpectStatus 409 { Invoke-RestMethod "$root/api/users/signup" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body $signup } 'duplicate username'
$login=@{username=$username;password='ExamplePassword123!'}|ConvertTo-Json
Invoke-RestMethod "$root/api/users/login" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body $login | Out-Null
$me=Invoke-RestMethod "$root/api/users/mypage" -WebSession $browser
Assert ($me.id -eq $member.id) 'mypage mismatch'
$facility=@{datasetCode='SMOKE';sourceKey=$username;name="테스트 체육관 $username";regionCode='서울특별시';regionName='서울특별시';roadAddress='서울 중구 테스트로 1';type='체육관';latitude=37.5;longitude=127.0;rawJson='{}'}|ConvertTo-Json
$importHeader=@{'X-Import-Key'=$env:IMPORT_KEY}
$facilityId=Invoke-RestMethod "$root/api/import/facilities" -Method Post -Headers $importHeader -ContentType 'application/json' -Body $facility
$facilityId2=Invoke-RestMethod "$root/api/import/facilities" -Method Post -Headers $importHeader -ContentType 'application/json' -Body $facility
Assert ($facilityId -eq $facilityId2) 'facility upsert duplicated'
$found=Invoke-RestMethod "$root/api/facilities?keyword=$username&regionCode=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C"
Assert ($found.page.totalElements -eq 1) 'facility search mismatch'
$review=@{rating=5;content='시설이 깨끗합니다'}|ConvertTo-Json
$createdReview=Invoke-RestMethod "$root/api/facilities/$facilityId/reviews" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body $review
Assert ($createdReview.rating -eq 5) 'review mismatch'
$reviews=Invoke-RestMethod "$root/api/facilities/$facilityId/reviews"
Assert ($reviews.page.totalElements -eq 1) 'review list mismatch'
$question=Invoke-RestMethod "$root/api/qna" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body (@{title='운동 질문';content='예약 가능할까요?'}|ConvertTo-Json)
$comment=Invoke-RestMethod "$root/api/qna/$($question.id)/comments" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body (@{content='확인해 주세요'}|ConvertTo-Json)
Assert ($comment.authorName -eq '홍길동') 'comment author mismatch'
$reply=Invoke-RestMethod "$root/api/qna/$($question.id)/comments" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body (@{parentId=$comment.id;content='추가 질문입니다'}|ConvertTo-Json)
Assert ($reply.parentId -eq $comment.id) 'nested comment parent mismatch'
$comments=Invoke-RestMethod "$root/api/qna/$($question.id)/comments"
Assert ($comments.Count -eq 2 -and $comments[1].parentId -eq $comment.id) 'comment list mismatch'
try { Invoke-RestMethod "$root/api/reservations" -Method Post -WebSession $browser -Headers $csrfHeader -ContentType 'application/json' -Body (@{programId=999;startsAt='2030-01-01T10:00:00Z';endsAt='2030-01-01T11:00:00Z'}|ConvertTo-Json) | Out-Null; throw 'missing program accepted' }
catch { Assert ([int]$_.Exception.Response.StatusCode -eq 404) 'reservation wrong status' }
Invoke-RestMethod "$root/api/users/logout" -Method Post -WebSession $browser -Headers $csrfHeader | Out-Null
try { Invoke-RestMethod "$root/api/users/mypage" -WebSession $browser | Out-Null; throw 'logout failed' }
catch { Assert ([int]$_.Exception.Response.StatusCode -eq 401) 'logout wrong status' }
Write-Output 'PASS signup login mypage facility import/upsert/search review qna/comment reservation 404 logout'
