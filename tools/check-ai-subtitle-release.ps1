[CmdletBinding()]
param(
    [string]$Repository = "CometDash77/smarttube",
    [string]$Tag = "ai-subtitle-test-2026.09.13-r5"
)

if ($Repository -notmatch "^([^/]+)/([^/]+)$") {
    throw "Repository must use the owner/name form."
}

$owner = $Matches[1]
$name = $Matches[2]
$env:GITHUB_TOKEN = $null

$query = @'
query ($owner: String!, $name: String!, $tag: String!) {
  repository(owner: $owner, name: $name) {
    object(expression: $tag) {
      ... on Commit {
        oid
        checkSuites(first: 20) {
          nodes {
            status
            conclusion
            workflowRun {
              databaseId
              workflow { name }
            }
          }
        }
      }
    }
    release(tagName: $tag) {
      name
      tagName
      isPrerelease
      isDraft
      url
      releaseAssets(first: 20) {
        nodes { name size downloadUrl }
      }
    }
  }
}
'@

$raw = gh api graphql -f query=$query -F owner=$owner -F name=$name -F tag=$Tag
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$repositoryState = ($raw | ConvertFrom-Json).data.repository
$commit = $repositoryState.object
$release = $repositoryState.release

if (-not $commit) {
    throw "Tag '$Tag' does not resolve to a commit in '$Repository'."
}

$checks = @($commit.checkSuites.nodes | ForEach-Object {
    [ordered]@{
        workflow = $_.workflowRun.workflow.name
        runId = $_.workflowRun.databaseId
        status = $_.status
        conclusion = $_.conclusion
    }
})

[ordered]@{
    repository = $Repository
    tag = $Tag
    commit = $commit.oid
    checks = $checks
    release = if ($release) {
        [ordered]@{
            name = $release.name
            prerelease = $release.isPrerelease
            url = $release.url
            assets = @($release.releaseAssets.nodes | ForEach-Object {
                [ordered]@{
                    name = $_.name
                    size = $_.size
                    downloadUrl = $_.downloadUrl
                }
            })
        }
    } else {
        $null
    }
} | ConvertTo-Json -Depth 6

if (-not $release -or $release.isDraft -or -not $release.isPrerelease -or $checks.Count -eq 0 -or
        ($checks | Where-Object { $_.status -ne "COMPLETED" -or $_.conclusion -ne "SUCCESS" })) {
    exit 1
}

foreach ($arch in @('universal', 'armeabi-v7a', 'arm64-v8a', 'x86')) {
    $assets = @($release.releaseAssets.nodes | Where-Object { $_.name.EndsWith("_$arch.apk") -and $_.size -gt 0 })
    if ($assets.Count -ne 1) {
        Write-Error "Release must contain exactly one non-empty APK for $arch."
        exit 1
    }
}
