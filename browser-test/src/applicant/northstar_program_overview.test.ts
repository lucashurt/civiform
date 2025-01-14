import {expect, test} from '../support/civiform_fixtures'
import {
  enableFeatureFlag,
  loginAsAdmin,
  logout,
  loginAsTrustedIntermediary,
  ClientInformation,
  loginAsTestUser,
} from '../support'

test.describe('Applicant program overview', {tag: ['@northstar']}, () => {
  const programName = 'test'
  const questionText = 'This is a text question'

  test.beforeEach(async ({page, adminPrograms, adminQuestions}) => {
    await enableFeatureFlag(page, 'north_star_applicant_ui')

    await test.step('create a new program with one text question', async () => {
      await loginAsAdmin(page)
      await adminQuestions.addTextQuestion({
        questionName: 'text question',
        questionText: questionText,
      })
      await adminPrograms.addProgram(programName)
      await adminPrograms.editProgramBlockUsingSpec(programName, {
        description: 'First block',
        questions: [{name: 'text question', isOptional: true}],
      })
      await adminPrograms.gotoAdminProgramsPage()
      await adminPrograms.publishProgram(programName)
      await logout(page)
    })
  })

  test.describe('after starting an application', () => {
    test.beforeEach(async ({applicantQuestions}) => {
      await applicantQuestions.applyProgram(programName, true)
      await applicantQuestions.answerTextQuestion('first answer')
      await applicantQuestions.clickContinue()
    })
    test('takes guests and logged in users to the program overview', async ({
      page,
      applicantQuestions,
      applicantProgramOverview: applicantProgramOverview,
    }) => {
      // Exercise guest path
      await page.goto(`/programs/${programName}`)
      await applicantProgramOverview.expectProgramOverviewPage()

      await logout(page)

      // Exercise test user path
      await loginAsTestUser(page)
      await applicantQuestions.applyProgram(programName, true)
      await applicantQuestions.answerTextQuestion('first answer')
      await applicantQuestions.clickContinue()

      await page.goto(`/programs/${programName}`)
      await applicantProgramOverview.expectProgramOverviewPage()
    })
  })

  test('can view program overview', async ({
    page,
    applicantProgramOverview: applicantProgramOverview,
  }) => {
    await page.goto(`/programs/${programName}`)

    await applicantProgramOverview.expectProgramOverviewPage()
    expect(await page.title()).toBe('test - Program Overview')
  })

  test('redirects to disabled program info page when program is disabled', async ({
    page,
    adminPrograms,
  }) => {
    await enableFeatureFlag(page, 'disabled_visibility_condition_enabled')
    const disabledProgramName = 'dis'

    await test.step('create a new disabled program', async () => {
      await loginAsAdmin(page)
      await adminPrograms.addDisabledProgram(disabledProgramName)
      await adminPrograms.publishAllDrafts()
      await logout(page)
    })

    await test.step(`opens the deep link of the disabled program and gets redirected to an error info page`, async () => {
      await page.goto(`/programs/${disabledProgramName}`)
      expect(page.url()).toContain('/disabled')
      await expect(
        page.getByRole('heading', {
          name: 'This program is no longer available',
        }),
      ).toBeVisible()
    })

    await test.step(`clicks on visit homepage button and it takes me to home page`, async () => {
      await page.click('#visit-home-page-button')
      expect(page.url()).toContain('/programs')
      await expect(
        page.getByRole('heading', {
          name: 'Apply to programs in one place',
        }),
      ).toBeVisible()
    })
  })

  test('trusted intermediary can view program overview with applicant id in the URL', async ({
    page,
    tiDashboard,
    applicantProgramOverview: applicantProgramOverview,
  }) => {
    await loginAsTrustedIntermediary(page)
    const client: ClientInformation = {
      emailAddress: 'test@sample.com',
      firstName: 'first',
      middleName: 'middle',
      lastName: 'last',
      dobDate: '2021-06-10',
    }
    await tiDashboard.createClient(client)
    await tiDashboard.expectDashboardContainClient(client)
    await tiDashboard.clickOnViewApplications()

    const url = page.url()
    expect(url).toContain('/applicants/')

    await page.goto(`${url}/${programName}`)

    await applicantProgramOverview.expectProgramOverviewPage()
  })

  test('Going to a deep link does not retain redirect in session', async ({
    page,
  }) => {
    // Go to a deep link
    await page.goto(`/programs/${programName}`)

    // Logging out should not take us back to the program overview, but rather
    // to the program index page.
    await logout(page)

    await expect(
      page.getByRole('heading', {
        name: 'Apply to programs in one place',
      }),
    ).toBeAttached()
  })
})